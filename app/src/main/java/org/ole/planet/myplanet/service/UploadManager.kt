package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import android.text.TextUtils
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.realm.Realm
import io.realm.RealmResults
import java.io.File
import java.io.IOException
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.datamanager.ApiClient.client
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.FileUploadService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.model.RealmAchievement
import org.ole.planet.myplanet.model.RealmApkLog
import org.ole.planet.myplanet.model.RealmCourseActivity
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyPersonal
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNewsLog
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.model.RealmResourceActivity
import org.ole.planet.myplanet.model.RealmSearchActivity
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmitPhotos
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.VersionUtils.getAndroidId
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

private const val BATCH_SIZE = 50

private inline fun <T> Iterable<T>.processInBatches(action: (T) -> Unit) {
    chunked(BATCH_SIZE).forEach { chunk ->
        chunk.forEach { item ->
            action(item)
        }
    }
}

@Singleton
class UploadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseService: DatabaseService,
    @AppPreferences private val pref: SharedPreferences
) : FileUploadService() {

    // Backward compatibility constructor for code that still uses singleton pattern
    constructor(context: Context) : this(
        context,
        DatabaseService(context),
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    private val gson = Gson()

    private fun getRealm(): Realm {
        return databaseService.realmInstance
    }

    private fun uploadNewsActivities() {
        val apiInterface = client?.create(ApiInterface::class.java)
        val realm = getRealm()
        realm.executeTransactionAsync { realm: Realm ->
            val newsLog: List<RealmNewsLog> = realm.where(RealmNewsLog::class.java)
                .isNull("_id").or().isEmpty("_id")
                .findAll()

            newsLog.processInBatches { news ->
                    try {
                        val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/myplanet_activities", RealmNewsLog.serialize(news))?.execute()?.body()

                        if (`object` != null) {
                            news._id = getString("id", `object`)
                            news._rev = getString("rev", `object`)
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
            }
        }

    }

    fun uploadActivities(listener: SuccessListener?) {
        val apiInterface = client?.create(ApiInterface::class.java)
        val model = UserProfileDbHandler(MainApplication.context).userModel ?: run {
            listener?.onSuccess("Cannot upload activities: user model is null")
            return
        }

        if (model.isManager()) {
            listener?.onSuccess("Skipping activities upload for manager")
            return
        }

        try {
            apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/myplanet_activities", MyPlanet.getNormalMyPlanetActivities(MainApplication.context, pref, model))?.enqueue(object : Callback<JsonObject?> {
                override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {}

                override fun onFailure(call: Call<JsonObject?>, t: Throwable) {}
            })

            apiInterface?.getJsonObject(Utilities.header, "${Utilities.getUrl()}/myplanet_activities/${getAndroidId(MainApplication.context)}@${NetworkUtils.getUniqueIdentifier()}")?.enqueue(object : Callback<JsonObject?> {
                override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                    var `object` = response.body()

                    if (`object` != null) {
                        val usages = `object`.getAsJsonArray("usages")
                        usages.addAll(MyPlanet.getTabletUsages(context))
                        `object`.add("usages", usages)
                    } else {
                        `object` = MyPlanet.getMyPlanetActivities(context, pref, model)
                    }

                    apiInterface.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/myplanet_activities", `object`).enqueue(object : Callback<JsonObject?> {
                        override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                            listener?.onSuccess("My planet activities uploaded successfully")
                        }

                        override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                            listener?.onSuccess("Failed to upload activities: ${t.message}")
                        }
                    })
                }

                override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                    val `object` = MyPlanet.getMyPlanetActivities(context, pref, model)
                    apiInterface.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/myplanet_activities", `object`).enqueue(object : Callback<JsonObject?> {
                        override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                            listener?.onSuccess("My planet activities uploaded successfully")
                        }

                        override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                            listener?.onSuccess("Failed to upload activities: ${t.message}")
                        }
                    })
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
            listener?.onSuccess("Failed to upload activities: ${e.message}")
        }
    }

    fun uploadExamResult(listener: SuccessListener) {
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        realm.executeTransactionAsync({ realm: Realm ->
            val submissions: List<RealmSubmission> = realm.where(RealmSubmission::class.java).findAll()

            submissions.processInBatches { sub ->
                    try {
                        if ((sub.answers?.size ?: 0) > 0) {
                            RealmSubmission.continueResultUpload(sub, apiInterface, realm, context)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
            }
        }, {
            uploadCourseProgress()
            listener.onSuccess("Result sync completed successfully")
        }) { e: Throwable ->
            e.printStackTrace()
            listener.onSuccess("Error during result sync: ${e.message}")
        }
    }

    private fun createImage(user: RealmUserModel?, imgObject: JsonObject?): JsonObject {
        val `object` = JsonObject()
        `object`.addProperty("title", getString("fileName", imgObject))
        `object`.addProperty("createdDate", Date().time)
        `object`.addProperty("filename", getString("fileName", imgObject))
        `object`.addProperty("addedBy", user!!.id)
        `object`.addProperty("private", true)
        `object`.addProperty("resideOn", user.parentCode)
        `object`.addProperty("sourcePlanet", user.planetCode)
        val object1 = JsonObject()
        `object`.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
        `object`.addProperty("deviceName", NetworkUtils.getDeviceName())
        `object`.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(MainApplication.context))
        `object`.add("privateFor", object1)
        `object`.addProperty("mediaType", "image")
        return `object`
    }

    fun uploadAchievement() {
        val realm = getRealm()
        realm.executeTransactionAsync { realm: Realm ->
            val list: List<RealmAchievement> = realm.where(RealmAchievement::class.java).findAll()
            list.processInBatches { sub ->
                try {
                    if (sub._id?.startsWith("guest") == true) {
                        return@processInBatches
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }

    }

    private fun uploadCourseProgress() {
        val apiInterface = client?.create(ApiInterface::class.java)
        val realm = getRealm()
        realm.executeTransactionAsync { realm: Realm ->
            val data: List<RealmCourseProgress> = realm.where(RealmCourseProgress::class.java).isNull("_id").findAll()
            var successCount = 0
            var skipCount = 0
            var errorCount = 0

            data.processInBatches { sub ->
                    try {
                        if (sub.userId?.startsWith("guest") == true) {
                            skipCount++
                            return@processInBatches
                        }

                        val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/courses_progress", RealmCourseProgress.serializeProgress(sub))?.execute()?.body()
                        if (`object` != null) {
                            sub._id = getString("id", `object`)
                            sub._rev = getString("rev", `object`)
                            successCount++
                        } else {
                            errorCount++
                        }
                    } catch (e: IOException) {
                        errorCount++
                        e.printStackTrace()
                    }
            }

        }
    }

    fun uploadFeedback(listener: SuccessListener) {
        val apiInterface = client?.create(ApiInterface::class.java)
        val realm = getRealm()
        realm.executeTransactionAsync(Realm.Transaction { realm: Realm ->
            val feedbacks: List<RealmFeedback> = realm.where(RealmFeedback::class.java).findAll()

            if (feedbacks.isEmpty()) {
                return@Transaction
            }

            var successCount = 0
            var errorCount = 0

            feedbacks.processInBatches { feedback ->
                try {
                    val res: Response<JsonObject>? = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/feedback", RealmFeedback.serializeFeedback(feedback))?.execute()

                    val r = res?.body()
                    if (r != null) {
                        val revElement = r["rev"]
                        val idElement = r["id"]
                        if (revElement != null && idElement != null) {
                            feedback._rev = revElement.asString
                            feedback._id = idElement.asString
                            successCount++
                        } else {
                            errorCount++
                        }
                    } else {
                        errorCount++
                    }
                } catch (e: IOException) {
                    errorCount++
                    e.printStackTrace()
                }
            }
        }, {
            listener.onSuccess("Feedback sync completed successfully")
        }, { error ->
            listener.onSuccess("Feedback sync failed: ${error.message}")
            error.printStackTrace()
        })
    }

    fun uploadSubmitPhotos(listener: SuccessListener?) {
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)
        realm.executeTransactionAsync { realm: Realm ->
            val data: List<RealmSubmitPhotos> = realm.where(RealmSubmitPhotos::class.java).equalTo("uploaded", false).findAll()
            data.processInBatches { sub ->
                    try {
                        val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/submissions", RealmSubmitPhotos.serializeRealmSubmitPhotos(sub))?.execute()?.body()
                        if (`object` != null) {
                            val rev = getString("rev", `object`)
                            val id = getString("id", `object`)
                            sub.uploaded = true
                            sub._rev = rev
                            sub._id = id
                            uploadAttachment(id, rev, sub, listener!!)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
            }
            if (data.isEmpty()) {
                listener?.onSuccess("No photos to upload")
            }
        }
    }

    fun uploadResource(listener: SuccessListener?) {
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        realm.executeTransactionAsync({ realm: Realm ->
            val user = realm.where(RealmUserModel::class.java)
                .equalTo("id", pref.getString("userId", ""))
                .findFirst()

            val data: List<RealmMyLibrary> = realm.where(RealmMyLibrary::class.java)
                .isNull("_rev")
                .findAll()

            if (data.isEmpty()) {
                return@executeTransactionAsync
            }

            data.processInBatches { sub ->
                try {
                    val `object` = apiInterface?.postDoc(
                        Utilities.header,
                        "application/json",
                        "${Utilities.getUrl()}/resources",
                        RealmMyLibrary.serialize(sub, user)
                    )?.execute()?.body()

                    if (`object` != null) {
                        val rev = getString("rev", `object`)
                        val id = getString("id", `object`)
                        sub._rev = rev
                        sub._id = id
                        uploadAttachment(id, rev, sub, listener!!)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }, {
            listener?.onSuccess("No resources to upload")
        }) { error ->
            listener?.onSuccess("Resource upload failed: ${error.message}")
        }
    }

    fun uploadMyPersonal(personal: RealmMyPersonal, listener: SuccessListener) {
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        if (!personal.isUploaded) {
            apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/resources", RealmMyPersonal.serialize(personal, context))?.enqueue(object : Callback<JsonObject?> {
                override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                    val `object` = response.body()
                    if (`object` != null) {
                        try {
                            if (!realm.isInTransaction) {
                                realm.beginTransaction()
                            }
                            val rev = getString("rev", `object`)
                            val id = getString("id", `object`)
                            personal.isUploaded = true
                            personal._rev = rev
                            personal._id = id
                            realm.commitTransaction()

                            uploadAttachment(id, rev, personal, listener)
                        } catch (e: Exception) {
                            if (realm.isInTransaction) {
                                realm.cancelTransaction()
                            }
                            listener.onSuccess("Error updating personal resource: ${e.message}")
                        }
                    } else {
                        listener.onSuccess("Failed to upload personal resource: No response")
                    }
                }

                override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                    listener.onSuccess("Unable to upload resource: ${t.message}")
                }
            })
        } else {
            listener.onSuccess("Resource already uploaded")
        }
    }

    fun uploadTeamTask() {
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)
        realm.executeTransactionAsync { realm: Realm ->
            val list: List<RealmTeamTask> = realm.where(RealmTeamTask::class.java).findAll()
            val tasksToUpload = list.filter { task ->
                TextUtils.isEmpty(task._id) || task.isUpdated
            }

            tasksToUpload.processInBatches { task ->
                    try {
                        val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/tasks", RealmTeamTask.serialize(realm, task))?.execute()?.body()

                        if (`object` != null) {
                            val rev = getString("rev", `object`)
                            val id = getString("id", `object`)
                            task._rev = rev
                            task._id = id
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
            }
        }
    }

    fun uploadSubmissions() {
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        realm.executeTransactionAsync { realm: Realm ->
            val list: List<RealmSubmission> = realm.where(RealmSubmission::class.java)
                .equalTo("isUpdated", true).or().isEmpty("_id").findAll()

            list.processInBatches { submission ->
                    try {
                        val requestJson = RealmSubmission.serialize(realm, submission)
                        val response = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/submissions", requestJson)?.execute()

                        val jsonObject = response?.body()
                        if (jsonObject != null) {
                            val rev = getString("rev", jsonObject)
                            val id = getString("id", jsonObject)
                            submission._rev = rev
                            submission._id = id
                            submission.isUpdated = false
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
            }
        }
    }

    fun uploadTeams() {
        val apiInterface = client?.create(ApiInterface::class.java)
        val realm = getRealm()

        realm.executeTransactionAsync { realm: Realm ->
            val teams: List<RealmMyTeam> = realm.where(RealmMyTeam::class.java).equalTo("updated", true).findAll()
            teams.processInBatches { team ->
                    try {
                        val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/teams", RealmMyTeam.serialize(team))?.execute()?.body()
                        if (`object` != null) {
                            team._rev = getString("rev", `object`)
                            team.updated = false
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
            }
        }
    }

    fun uploadUserActivities(listener: SuccessListener) {
        val apiInterface = client?.create(ApiInterface::class.java)
        val model = UserProfileDbHandler(MainApplication.context).userModel ?: run {
            listener.onSuccess("Cannot upload user activities: user model is null")
            return
        }

        if (model.isManager()) {
            listener.onSuccess("Skipping user activities upload for manager")
            return
        }

        val realm = getRealm()
        realm.executeTransactionAsync({ transactionRealm: Realm ->
            val activities = transactionRealm.where(RealmOfflineActivity::class.java).isNull("_rev").equalTo("type", "login").findAll()

            activities.processInBatches { act ->
                try {
                    if (act.userId?.startsWith("guest") == true) {
                        return@processInBatches
                    }

                    val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/login_activities", RealmOfflineActivity.serializeLoginActivities(act, context))?.execute()?.body()
                    act.changeRev(`object`)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            uploadTeamActivities(transactionRealm, apiInterface)
        }, {
            realm.close()
            listener.onSuccess("User activities sync completed successfully")
        }) { e: Throwable ->
            realm.close()
            e.printStackTrace()
            listener.onSuccess(e.message)
        }
    }

    fun uploadTeamActivities(realm: Realm, apiInterface: ApiInterface?) {
        val logs = realm.where(RealmTeamLog::class.java).isNull("_rev").findAll()
        logs.processInBatches { log ->
                try {
                    val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/team_activities", RealmTeamLog.serializeTeamActivities(log, context))?.execute()?.body()
                    if (`object` != null) {
                        log._id = getString("id", `object`)
                        log._rev = getString("rev", `object`)
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
        }
    }

    fun uploadRating() {
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        realm.executeTransactionAsync { realm: Realm ->
            val activities = realm.where(RealmRating::class.java).equalTo("isUpdated", true).findAll()
            activities.processInBatches { act ->
                    try {
                        if (act.userId?.startsWith("guest") == true) {
                            return@processInBatches
                        }

                        val `object`: Response<JsonObject>? =
                            if (TextUtils.isEmpty(act._id)) {
                                apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/ratings", RealmRating.serializeRating(act))?.execute()
                            } else {
                                apiInterface?.putDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/ratings/" + act._id, RealmRating.serializeRating(act))?.execute()
                            }
                        if (`object`?.body() != null) {
                            act._id = getString("id", `object`.body())
                            act._rev = getString("rev", `object`.body())
                            act.isUpdated = false
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
            }
        }
    }

    fun uploadNews() {
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        realm.executeTransactionAsync { realm: Realm ->
            val activities = realm.where(RealmNews::class.java).findAll()
            activities.processInBatches { act ->
                    try {
                        if (act.userId?.startsWith("guest") == true) {
                            return@processInBatches
                        }

                        val `object` = RealmNews.serializeNews(act)
                        val image = act.imagesArray
                        val user = realm.where(RealmUserModel::class.java).equalTo("id", pref.getString("userId", "")).findFirst()

                        if (act.imageUrls != null && act.imageUrls?.isNotEmpty() == true) {
                            act.imageUrls?.chunked(5)?.forEach { imageChunk ->
                                imageChunk.forEach { imageObject ->
                                    val imgObject = gson.fromJson(imageObject, JsonObject::class.java)
                                    val ob = createImage(user, imgObject)
                                    val response = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/resources", ob)?.execute()?.body()

                                    val rev = getString("rev", response)
                                    val id = getString("id", response)
                                    val f = File(getString("imageUrl", imgObject))
                                    val name = FileUtils.getFileNameFromUrl(getString("imageUrl", imgObject))
                                    val format = "%s/resources/%s/%s"
                                    val connection = f.toURI().toURL().openConnection()
                                    val mimeType = connection.contentType
                                    val body = FileUtils.fullyReadFileToBytes(f)
                                        .toRequestBody("application/octet-stream".toMediaTypeOrNull())
                                    val url = String.format(format, Utilities.getUrl(), id, name)

                                    val res = apiInterface?.uploadResource(getHeaderMap(mimeType, rev), url, body)?.execute()
                                    val attachment = res?.body()

                                    val resourceObject = JsonObject()
                                    resourceObject.addProperty("resourceId", getString("id", attachment))
                                    resourceObject.addProperty("filename", getString("fileName", imgObject))
                                    val markdown = "![](resources/" + getString("id", attachment) + "/" + getString("fileName", imgObject) + ")"
                                    resourceObject.addProperty("markdown", markdown)

                                    var msg = getString("message", `object`)
                                    msg += """
                                    
                                    $markdown
                                    """.trimIndent()
                                    `object`.addProperty("message", msg)
                                    image.add(resourceObject)
                                }
                            }
                        }

                        act.images = gson.toJson(image)
                        `object`.add("images", image)

                        val newsUploadResponse: Response<JsonObject>? =
                            if (TextUtils.isEmpty(act._id)) {
                                apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/news", `object`)?.execute()
                            } else {
                                apiInterface?.putDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/news/" + act._id, `object`)?.execute()
                            }
                        if (newsUploadResponse?.body() != null) {
                            act.imageUrls?.clear()
                            act._id = getString("id", newsUploadResponse.body())
                            act._rev = getString("rev", newsUploadResponse.body())
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        uploadNewsActivities()
    }

    fun uploadCrashLog() {
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        try {
            val hasLooper = Looper.myLooper() != null

            if (hasLooper) {
                realm.executeTransactionAsync { realm: Realm ->
                    uploadCrashLogData(realm, apiInterface)
                }
            } else {
                realm.executeTransaction { realm: Realm ->
                    uploadCrashLogData(realm, apiInterface)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun uploadCrashLogData(realm: Realm, apiInterface: ApiInterface?) {
        val logs: RealmResults<RealmApkLog> = realm.where(RealmApkLog::class.java).isNull("_rev").findAll()

        logs.processInBatches { act ->
                try {
                    val o = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/apk_logs", RealmApkLog.serialize(act, context))?.execute()?.body()

                    if (o != null) {
                        act._rev = getString("rev", o)
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
        }
    }

    fun uploadSearchActivity() {
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)
        realm.executeTransactionAsync { realm: Realm ->
            val logs: RealmResults<RealmSearchActivity> = realm.where(RealmSearchActivity::class.java).isEmpty("_rev").findAll()
            logs.processInBatches { act ->
                    try {
                        val o = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/search_activities", act.serialize())?.execute()?.body()
                        if (o != null) {
                            act._rev = getString("rev", o)
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
            }
        }

    }

    fun uploadResourceActivities(type: String) {
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        val db = if (type == "sync") {
            "admin_activities"
        } else {
            "resource_activities"
        }

        realm.executeTransactionAsync { realm: Realm ->
            val activities: RealmResults<RealmResourceActivity> =
                if (type == "sync") {
                    realm.where(RealmResourceActivity::class.java).isNull("_rev").equalTo("type", "sync").findAll()
                } else {
                    realm.where(RealmResourceActivity::class.java).isNull("_rev").notEqualTo("type", "sync").findAll()
                }
            activities.processInBatches { act ->
                    try {
                        val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/" + db, RealmResourceActivity.serializeResourceActivities(act))?.execute()?.body()

                        if (`object` != null) {
                            act._rev = getString("rev", `object`)
                            act._id = getString("id", `object`)
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
            }
        }
    }

    fun uploadCourseActivities() {
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)

        realm.executeTransactionAsync { realm: Realm ->
            val activities: RealmResults<RealmCourseActivity> = realm.where(RealmCourseActivity::class.java).isNull("_rev").notEqualTo("type", "sync").findAll()
            activities.processInBatches { act ->
                    try {
                        val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/course_activities", RealmCourseActivity.serializeSerialize(act))?.execute()?.body()

                        if (`object` != null) {
                            act._rev = getString("rev", `object`)
                            act._id = getString("id", `object`)
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
            }
        }
    }

    fun uploadMeetups() {
        val realm = getRealm()
        val apiInterface = client?.create(ApiInterface::class.java)
        realm.executeTransactionAsync { realm: Realm ->
            val meetups: List<RealmMeetup> = realm.where(RealmMeetup::class.java).findAll()
            meetups.processInBatches { meetup ->
                    try {
                        val meetupJson = RealmMeetup.serialize(meetup)
                        val `object` = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/meetups", meetupJson)?.execute()?.body()

                        if (`object` != null) {
                            meetup.meetupId = getString("id", `object`)
                            meetup.meetupIdRev = getString("rev", `object`)
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
            }
        }
    }
}
