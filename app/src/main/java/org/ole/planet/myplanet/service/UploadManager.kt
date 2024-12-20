package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.kotlin.Realm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.RequestBody
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.datamanager.ApiClient.client
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.FileUploadService
import org.ole.planet.myplanet.model.MyPlanet.Companion.getMyPlanetActivities
import org.ole.planet.myplanet.model.MyPlanet.Companion.getNormalMyPlanetActivities
import org.ole.planet.myplanet.model.MyPlanet.Companion.getTabletUsages
import org.ole.planet.myplanet.model.RealmAchievement
import org.ole.planet.myplanet.model.RealmApkLog
import org.ole.planet.myplanet.model.RealmApkLog.Companion.serialize
import org.ole.planet.myplanet.model.RealmCourseActivity
import org.ole.planet.myplanet.model.RealmCourseActivity.Companion.serializeSerialize
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmCourseProgress.Companion.serializeProgress
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.RealmFeedback.Companion.serializeFeedback
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.serialize
import org.ole.planet.myplanet.model.RealmMyPersonal
import org.ole.planet.myplanet.model.RealmMyPersonal.Companion.serialize
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.serialize
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNews.Companion.serializeNews
import org.ole.planet.myplanet.model.RealmNewsLog
import org.ole.planet.myplanet.model.RealmNewsLog.Companion.serialize
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmOfflineActivity.Companion.serializeLoginActivities
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.model.RealmRating.Companion.serializeRating
import org.ole.planet.myplanet.model.RealmResourceActivity
import org.ole.planet.myplanet.model.RealmResourceActivity.Companion.serializeResourceActivities
import org.ole.planet.myplanet.model.RealmSearchActivity
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmission.Companion.continueResultUpload
import org.ole.planet.myplanet.model.RealmSubmitPhotos
import org.ole.planet.myplanet.model.RealmSubmitPhotos.Companion.serializeRealmSubmitPhotos
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmTeamLog.Companion.serializeTeamActivities
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmTeamTask.Companion.serialize
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.FileUtils.fullyReadFileToBytes
import org.ole.planet.myplanet.utilities.FileUtils.getFileNameFromUrl
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.NetworkUtils.getCustomDeviceName
import org.ole.planet.myplanet.utilities.NetworkUtils.getDeviceName
import org.ole.planet.myplanet.utilities.NetworkUtils.getUniqueIdentifier
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.VersionUtils.getAndroidId
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.util.Date

class UploadManager(var context: Context) : FileUploadService() {
    var pref: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dbService: DatabaseService = DatabaseService()
    private val realm: Realm by lazy { dbService.realmInstance }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val apiInterface = client?.create(ApiInterface::class.java)

    private fun uploadNewsActivities() {
        scope.launch {
            realm.write {
                val newsLog = query<RealmNewsLog>(RealmNewsLog::class).query("_id == null || _id == ''").find()
                for (news in newsLog) {
                    try {
                        val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/myplanet_activities", serialize(news))?.execute()?.body()
                        if (`object` != null) {
                            findLatest(news)?.apply {
                                _id = getString("id", `object`)
                                _rev = getString("rev", `object`)
                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun uploadActivities(listener: SuccessListener?) {
        val model = UserProfileDbHandler(MainApplication.context).userModel ?: return
        if (model.isManager()) return
        try {
            apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/myplanet_activities", getNormalMyPlanetActivities(MainApplication.context, pref, model))?.enqueue(object : Callback<JsonObject?> {
                override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {}
                override fun onFailure(call: Call<JsonObject?>, t: Throwable) {}
            })
            apiInterface?.getJsonObject(Utilities.header, "${Utilities.getUrl()}/myplanet_activities/${getAndroidId(MainApplication.context)}@${getUniqueIdentifier()}")?.enqueue(object : Callback<JsonObject?> {
                override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                    var `object` = response.body()
                    if (`object` != null) {
                        val usages = `object`.getAsJsonArray("usages")
                        usages.addAll(getTabletUsages(context))
                        `object`.add("usages", usages)
                    } else {
                        `object` = getMyPlanetActivities(context, pref, model)
                    }
                    apiInterface.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/myplanet_activities", `object`).enqueue(object : Callback<JsonObject?> {
                        override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                            listener?.onSuccess("My planet activities uploaded successfully")
                        }

                        override fun onFailure(call: Call<JsonObject?>, t: Throwable) {}
                    })
                }

                override fun onFailure(call: Call<JsonObject?>, t: Throwable) {}
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun uploadExamResult(listener: SuccessListener) {
        val submissions = realm.query<RealmSubmission>(RealmSubmission::class).find()
        scope.launch {
            try {
                submissions.forEach { sub ->
                    try {
                        if (sub.answers.isNotEmpty()) {
                            withContext(Dispatchers.IO) {
                                continueResultUpload(sub, apiInterface, realm)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                withContext(Dispatchers.Main) {
                    listener.onSuccess("Result sync completed successfully")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            uploadCourseProgress()
        }
    }

    private fun createImage(user: RealmUserModel?, imgObject: JsonObject?): JsonObject {
        val `object` = JsonObject()
        `object`.addProperty("title", getString("fileName", imgObject))
        `object`.addProperty("createdDate", Date().time)
        `object`.addProperty("filename", getString("fileName", imgObject))
        `object`.addProperty("addedBy", user?.id)
        `object`.addProperty("private", true)
        `object`.addProperty("resideOn", user?.parentCode)
        `object`.addProperty("sourcePlanet", user?.planetCode)
        val object1 = JsonObject()
        `object`.addProperty("androidId", getUniqueIdentifier())
        `object`.addProperty("deviceName", getDeviceName())
        `object`.addProperty("customDeviceName", getCustomDeviceName(MainApplication.context))
        `object`.add("privateFor", object1)
        `object`.addProperty("mediaType", "image")
        return `object`
    }

    fun uploadAchievement() {
        scope.launch {
            realm.write {
                val list = query<RealmAchievement>(RealmAchievement::class).find()
                for (sub in list) {
                    try {
                        if (sub._id.startsWith("guest") == true) {
                            continue
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun uploadCourseProgress() {
        scope.launch {
            realm.write {
                val data = query<RealmCourseProgress>(RealmCourseProgress::class).query("_id == null").find()
                for (sub in data) {
                    try {
                        if (sub.userId.startsWith("guest") == true) {
                            continue
                        }
                        val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/courses_progress", serializeProgress(sub))?.execute()?.body()
                        if (`object` != null) {
                            findLatest(sub)?.apply {
                                _id = getString("id", `object`)
                                _rev = getString("rev", `object`)
                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun uploadFeedback(listener: SuccessListener) {
        scope.launch {
            try {
                realm.write {
                    val feedbacks = query<RealmFeedback>(RealmFeedback::class).find()
                    for (feedback in feedbacks) {
                        try {
                            val res = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/feedback", serializeFeedback(feedback))?.execute()
                            val r = res?.body()
                            if (r != null) {
                                val revElement = r["rev"]
                                val idElement = r["id"]
                                if (revElement != null && idElement != null) {
                                    findLatest(feedback)?.apply {
                                        _rev = revElement.asString
                                        _id = idElement.asString
                                    }
                                }
                            }
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    listener.onSuccess("Feedback sync completed successfully")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun uploadSubmitPhotos(listener: SuccessListener?) {
        scope.launch {
            realm.write {
                val data = query<RealmSubmitPhotos>(RealmSubmitPhotos::class).query("uploaded == false").find()
                for (sub in data) {
                    try {
                        val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/submissions", serializeRealmSubmitPhotos(sub))?.execute()?.body()

                        if (`object` != null) {
                            findLatest(sub)?.apply {
                                val rev = getString("rev", `object`)
                                val id = getString("id", `object`)
                                uploaded = true
                                _rev = rev
                                _id = id
                            }
                            uploadAttachment(getString("id", `object`), getString("rev", `object`), sub, listener!!)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun uploadResource(listener: SuccessListener?) {
        scope.launch {
            realm.write {
                val user = query<RealmUserModel>(RealmUserModel::class).query("id == $0", pref.getString("userId", "")).first().find()
                val data = query<RealmMyLibrary>(RealmMyLibrary::class).query("_rev == null").find()

                for (sub in data) {
                    try {
                        val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/resources", serialize(sub, user))?.execute()?.body()

                        if (`object` != null) {
                            findLatest(sub)?.apply {
                                _rev = getString("rev", `object`)
                                _id = getString("id", `object`)
                            }
                            uploadAttachment(getString("id", `object`), getString("rev", `object`), sub, listener!!)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun uploadMyPersonal(personal: RealmMyPersonal, listener: SuccessListener) {
        if (!personal.isUploaded) {
            apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/resources", serialize(personal, context))?.enqueue(object : Callback<JsonObject?> {
                override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                    val `object` = response.body()
                    if (`object` != null) {
                        scope.launch {
                            realm.write {
                                findLatest(personal)?.apply {
                                    val rev = getString("rev", `object`)
                                    val id = getString("id", `object`)
                                    isUploaded = true
                                    _rev = rev
                                    _id = id
                                }
                            }
                            uploadAttachment(getString("id", `object`), getString("rev", `object`), personal, listener)
                        }
                    }
                }

                override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                    listener.onSuccess("Unable to upload resource")
                }
            })
        }
    }

    fun uploadTeamTask() {
        scope.launch {
            realm.write {
                val list = query<RealmTeamTask>(RealmTeamTask::class).find()
                for (task in list) {
                    if (TextUtils.isEmpty(task._id) || task.isUpdated) {
                        try {
                            val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/tasks", serialize(realm, task))?.execute()?.body()

                            if (`object` != null) {
                                findLatest(task)?.apply {
                                    _rev = getString("rev", `object`)
                                    _id = getString("id", `object`)
                                }
                            }
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    fun uploadTeams() {
        scope.launch {
            realm.write {
                val teams = query<RealmMyTeam>(RealmMyTeam::class).query("updated == true").find()
                for (team in teams) {
                    try {
                        val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/teams", serialize(team))?.execute()?.body()

                        if (`object` != null) {
                            findLatest(team)?.apply {
                                _rev = getString("rev", `object`)
                                updated = false
                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun uploadUserActivities(listener: SuccessListener) {
        val model = UserProfileDbHandler(MainApplication.context).userModel ?: return
        if (model.isManager()) return

        scope.launch {
            try {
                realm.write {
                    val activities = query<RealmOfflineActivity>(RealmOfflineActivity::class).query("_rev == null AND type == 'login'").find()

                    for (act in activities) {
                        try {
                            if (act.userId?.startsWith("guest") == true) continue
                            val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/login_activities", serializeLoginActivities(act, context))?.execute()?.body()
                            findLatest(act)?.changeRev(`object`)
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }

                    uploadTeamActivities()
                }

                withContext(Dispatchers.Main) {
                    listener.onSuccess("Sync with server completed successfully")
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    listener.onSuccess(e.message)
                }
            }
        }
    }

    private fun uploadTeamActivities() {
        scope.launch {
            realm.write {
                val logs = query<RealmTeamLog>(RealmTeamLog::class).query("_rev == null").find()
                for (log in logs) {
                    try {
                        val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/team_activities", serializeTeamActivities(log, context))?.execute()?.body()

                        if (`object` != null) {
                            val latest = findLatest(log)
                            if (latest != null) {
                                latest._id = getString("id", `object`)
                                latest._rev = getString("rev", `object`)
                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun uploadRating() {
        scope.launch {
            realm.write {
                val activities = query<RealmRating>(RealmRating::class).query("isUpdated == true").find()
                for (act in activities) {
                    try {
                        if (act.userId?.startsWith("guest") == true) continue
                        val `object`: Response<JsonObject>? = if (TextUtils.isEmpty(act._id)) {
                            apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/ratings", serializeRating(act))?.execute()
                        } else {
                            apiInterface?.putDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/ratings/${act._id}", serializeRating(act))?.execute()
                        }

                        if (`object`?.body() != null) {
                            findLatest(act)?.apply {
                                _id = getString("id", `object`.body())
                                _rev = getString("rev", `object`.body())
                                isUpdated = false
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun uploadNews() {
        scope.launch {
            realm.write {
                val activities = query<RealmNews>(RealmNews::class).find()
                for (act in activities) {
                    try {
                        if (act.userId?.startsWith("guest") == true) {
                            continue
                        }
                        val `object` = serializeNews(act)
                        val image = JsonArray()
                        val user = query<RealmUserModel>(RealmUserModel::class).query("id == $0", pref.getString("userId", "")).first().find()

                        act.imageUrls?.let { urls ->
                            for (imageObject in urls) {
                                val imgObject = Gson().fromJson(imageObject, JsonObject::class.java)
                                val ob = createImage(user, imgObject)
                                val response = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/resources", ob)?.execute()?.body()

                                val rev = getString("rev", response)
                                val id = getString("id", response)
                                val f = File(getString("imageUrl", imgObject))
                                val name = getFileNameFromUrl(getString("imageUrl", imgObject))
                                val format = "%s/resources/%s/%s"
                                val connection = f.toURI().toURL().openConnection()
                                val mimeType = connection.contentType
                                val body = RequestBody.create(MediaType.parse("application/octet"), fullyReadFileToBytes(f))
                                val url = String.format(format, Utilities.getUrl(), id, name)
                                val res = apiInterface?.uploadResource(getHeaderMap(mimeType, rev), url, body)?.execute()
                                val attachment = res?.body()

                                val resourceObject = JsonObject()
                                resourceObject.addProperty("resourceId", getString("id", attachment))
                                resourceObject.addProperty("filename", getString("fileName", imgObject))
                                val markdown = "![](resources/${getString("id", attachment)}/${getString("fileName", imgObject)})"
                                resourceObject.addProperty("markdown", markdown)
                                var msg = getString("message", `object`)
                                msg += """
                                
                                $markdown
                                """.trimIndent()
                                `object`.addProperty("message", msg)
                                image.add(resourceObject)
                            }
                        }

                        findLatest(act)?.apply {
                            images = Gson().toJson(image)
                        }
                        `object`.add("images", image)

                        val newsUploadResponse: Response<JsonObject>? =
                            if (TextUtils.isEmpty(act._id)) {
                                apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/news", `object`)?.execute()
                            } else {
                                apiInterface?.putDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/news/${act._id}", `object`)?.execute()
                            }

                        if (newsUploadResponse?.body() != null) {
                            findLatest(act)?.apply {
                                imageUrls?.clear()
                                _id = getString("id", newsUploadResponse.body())
                                _rev = getString("rev", newsUploadResponse.body())
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        uploadNewsActivities()
    }

    fun uploadCrashLog() {
        scope.launch {
            try {
                realm.write {
                    val logs = query<RealmApkLog>(RealmApkLog::class).query("_rev == null").find()
                    for (act in logs) {
                        try {
                            val o = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/apk_logs", serialize(act, context))?.execute()?.body()
                            if (o != null) {
                                findLatest(act)?.apply {
                                    _rev = getString("rev", o)
                                }
                            }
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun uploadSearchActivity() {
        scope.launch {
            realm.write {
                val logs = query<RealmSearchActivity>(RealmSearchActivity::class).query("_rev == ''").find()
                for (act in logs) {
                    try {
                        val serializedData = act.serialize().let { kotlinxJson ->
                            Gson().fromJson(kotlinxJson.toString(), JsonObject::class.java)
                        }

                        val o = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/search_activities", serializedData)?.execute()?.body()

                        if (o != null) {
                            findLatest(act)?.apply {
                                _rev = getString("rev", o)
                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun uploadResourceActivities(type: String) {
        val db = if (type == "sync") "admin_activities" else "resource_activities"
        scope.launch {
            realm.write {
                val activities = if (type == "sync") {
                    query<RealmResourceActivity>(RealmResourceActivity::class)
                        .query("_rev == null && type == 'sync'").find()
                } else {
                    query<RealmResourceActivity>(RealmResourceActivity::class)
                        .query("_rev == null && type != 'sync'").find()
                }

                for (act in activities) {
                    try {
                        val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/$db", serializeResourceActivities(act))?.execute()?.body()
                        if (`object` != null) {
                            findLatest(act)?.apply {
                                _rev = getString("rev", `object`)
                                _id = getString("id", `object`)
                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun uploadCourseActivities() {
        scope.launch {
            realm.write {
                val activities = query<RealmCourseActivity>(RealmCourseActivity::class).query("_rev == null && type != 'sync'").find()
                for (act in activities) {
                    try {
                        val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/course_activities", serializeSerialize(act))?.execute()?.body()
                        if (`object` != null) {
                            findLatest(act)?.apply {
                                _rev = getString("rev", `object`)
                                _id = getString("id", `object`)
                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    companion object {
        var instance: UploadManager? = null
            get() {
                if (field == null) {
                    field = UploadManager(MainApplication.context)
                }
                return field
            }
            private set
    }
}
