package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmResults
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
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmMeetup.Companion.serialize
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
import org.ole.planet.myplanet.model.RealmSubmission.Companion.serialize
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
    private val dbService: DatabaseService = DatabaseService(context)
    lateinit var mRealm: Realm

    private fun uploadNewsActivities() {
        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm = dbService.realmInstance
        mRealm.executeTransactionAsync { realm: Realm ->
            val newsLog: List<RealmNewsLog> = realm.where(RealmNewsLog::class.java).isNull("_id").or().isEmpty("_id").findAll()
            for (news in newsLog) {
                try {
                    val `object` = apiInterface?.postDoc(Utilities.header, "application/json", Utilities.getUrl() + "/myplanet_activities", serialize(news))?.execute()?.body()
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
        val model = UserProfileDbHandler(MainApplication.context).userModel ?: return
        if (model.isManager()) return
        try {
            apiInterface?.postDoc(Utilities.header, "application/json",  "${Utilities.getUrl()}/myplanet_activities", getNormalMyPlanetActivities(MainApplication.context, pref, model))?.enqueue(object : Callback<JsonObject?> {
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
        mRealm = dbService.realmInstance
        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm.executeTransactionAsync(
            { realm: Realm ->
                val submissions: List<RealmSubmission> = realm.where(
                    RealmSubmission::class.java
                ).findAll()
                for (sub in submissions) {
                    try {
                        if ((sub.answers?.size ?: 0) > 0) {
                            continueResultUpload(sub, apiInterface, realm, context)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            },
            { listener.onSuccess("Result sync completed successfully") }) { e: Throwable -> e.printStackTrace() }
        uploadCourseProgress()
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
        `object`.addProperty("androidId", getUniqueIdentifier())
        `object`.addProperty("deviceName", getDeviceName())
        `object`.addProperty("customDeviceName", getCustomDeviceName(MainApplication.context))
        `object`.add("privateFor", object1)
        `object`.addProperty("mediaType", "image")
        return `object`
    }

    fun uploadAchievement() {
        mRealm = dbService.realmInstance
//        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm.executeTransactionAsync { realm: Realm ->
            val list: List<RealmAchievement> = realm.where(RealmAchievement::class.java).findAll()
            for (sub in list) {
                try {
                    if (sub._id?.startsWith("guest") == true) {
                        continue
                    }
//                    val ob = apiInterface?.putDoc(Utilities.header, "application/json", Utilities.getUrl() + "/achievements/" + sub.get_id(), serialize(sub))?.execute()?.body()
//                    if (ob == null) {
//                        val re = apiInterface?.postDoc(Utilities.header, "application/json", Utilities.getUrl() + "/achievements", serialize(sub))?.execute()?.errorBody()
//                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun uploadCourseProgress() {
        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm = dbService.realmInstance
        mRealm.executeTransactionAsync { realm: Realm ->
            val data: List<RealmCourseProgress> = realm.where(RealmCourseProgress::class.java).isNull("_id").findAll()
            for (sub in data) {
                try {
                    if (sub.userId?.startsWith("guest") == true) {
                        continue
                    }
                    val `object` = apiInterface?.postDoc(Utilities.header, "application/json", Utilities.getUrl() + "/courses_progress", serializeProgress(sub))?.execute()?.body()
                    if (`object` != null) {
                        sub._id = getString("id", `object`)
                        sub._rev = getString("rev", `object`)
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun uploadFeedback(listener: SuccessListener) {
        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm = dbService.realmInstance
        mRealm.executeTransactionAsync(Realm.Transaction { realm: Realm ->
            val feedbacks: List<RealmFeedback> = realm.where(RealmFeedback::class.java).findAll()
            for (feedback in feedbacks) {
                try {
                    val res: Response<JsonObject>? = apiInterface?.postDoc(Utilities.header, "application/json", Utilities.getUrl() + "/feedback", serializeFeedback(feedback))?.execute()
                    val r = res?.body()
                    if (r != null) {
                        val revElement = r["rev"]
                        val idElement = r["id"]
                        if (revElement != null && idElement != null) {
                            feedback._rev = revElement.asString
                            feedback._id = idElement.asString
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            } },
            Realm.Transaction.OnSuccess { listener.onSuccess("Feedback sync completed successfully") })
    }

    fun uploadSubmitPhotos(listener: SuccessListener?) {
        mRealm = DatabaseService(context).realmInstance
        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm.executeTransactionAsync { realm: Realm ->
            val data: List<RealmSubmitPhotos> = realm.where(RealmSubmitPhotos::class.java).equalTo("uploaded", false).findAll()
            for (sub in data) {
                try {
                    val `object` = apiInterface?.postDoc(Utilities.header, "application/json", Utilities.getUrl() + "/submissions", serializeRealmSubmitPhotos(sub))?.execute()?.body()
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
        }
    }

    fun uploadResource(listener: SuccessListener?) {
        mRealm = DatabaseService(context).realmInstance
        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm.executeTransactionAsync { realm: Realm ->
            val user = realm.where(RealmUserModel::class.java).equalTo("id", pref.getString("userId", "")).findFirst()
            val data: List<RealmMyLibrary> = realm.where(RealmMyLibrary::class.java).isNull("_rev").findAll()
            for (sub in data) {
                try {
                    val `object` = apiInterface?.postDoc(Utilities.header, "application/json", Utilities.getUrl() + "/resources", serialize(sub, user))?.execute()?.body()
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
        }
    }

    fun uploadMyPersonal(personal: RealmMyPersonal, listener: SuccessListener) {
        mRealm = DatabaseService(context).realmInstance
        val apiInterface = client?.create(ApiInterface::class.java)
        if (!personal.isUploaded) {
            apiInterface?.postDoc(Utilities.header, "application/json", Utilities.getUrl() + "/resources", serialize(personal, context))?.enqueue(object : Callback<JsonObject?> {
                override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                    val `object` = response.body()
                    if (`object` != null) {
                        if (!mRealm.isInTransaction) {
                            mRealm.beginTransaction()
                        }
                        val rev = getString("rev", `object`)
                        val id = getString("id", `object`)
                        personal.isUploaded = true
                        personal._rev = rev
                        personal._id = id
                        mRealm.commitTransaction()
                        uploadAttachment(id, rev, personal, listener)
                    }
                }

                override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                    listener.onSuccess("Unable to upload resource")
                }
            })
        }
    }

    fun uploadTeamTask() {
        mRealm = DatabaseService(context).realmInstance
        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm.executeTransactionAsync { realm: Realm ->
            val list: List<RealmTeamTask> = realm.where(RealmTeamTask::class.java).findAll()
            for (task in list) {
                if (TextUtils.isEmpty(task._id) || task.isUpdated) {
                    var `object`: JsonObject?
                    try {
                        `object` = apiInterface?.postDoc(Utilities.header, "application/json", Utilities.getUrl() + "/tasks", serialize(realm, task))?.execute()?.body()
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
    }

    fun uploadSubmissions() {
       mRealm = dbService.realmInstance
        val apiInterface = client?.create(ApiInterface::class.java)

        mRealm.executeTransactionAsync { realm: Realm ->
            val list: List<RealmSubmission> = realm.where(RealmSubmission::class.java)
                .equalTo("isUpdated", true).or().isEmpty("_id").findAll()
            for (submission in list) {
                var jsonObject: JsonObject?
                try {
                    val requestJson = serialize(realm, submission)
                    val response = apiInterface?.postDoc(Utilities.header, "application/json", "${Utilities.getUrl()}/submissions", requestJson)?.execute()
                    jsonObject = response?.body()

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
        mRealm = dbService.realmInstance
        mRealm.executeTransactionAsync { realm: Realm ->
            val teams: List<RealmMyTeam> = realm.where(RealmMyTeam::class.java).equalTo("updated", true).findAll()
            for (team in teams) {
                try {
                    val `object` = apiInterface?.postDoc(Utilities.header, "application/json", Utilities.getUrl() + "/teams", serialize(team))?.execute()?.body()
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
        mRealm = dbService.realmInstance
        val model = UserProfileDbHandler(MainApplication.context).userModel ?: return
        if (model.isManager()) {
            return
        }
        mRealm.executeTransactionAsync({ realm: Realm ->
            val activities = realm.where(RealmOfflineActivity::class.java).isNull("_rev").equalTo("type", "login").findAll()
            for (act in activities) {
                try {
                    if (act.userId?.startsWith("guest") == true) {
                        continue
                    }
                    val `object` = apiInterface?.postDoc(Utilities.header, "application/json", Utilities.getUrl() + "/login_activities", serializeLoginActivities(act, context))?.execute()?.body()
                    act.changeRev(`object`)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            uploadTeamActivities(realm, apiInterface) },
            { listener.onSuccess("Sync with server completed successfully") }) { e: Throwable ->
            listener.onSuccess(e.message)
        }
    }

    private fun uploadTeamActivities(realm: Realm, apiInterface: ApiInterface?) {
        val logs = realm.where(RealmTeamLog::class.java).isNull("_rev").findAll()
        for (log in logs) {
            try {
                val `object` = apiInterface?.postDoc(Utilities.header, "application/json", Utilities.getUrl() + "/team_activities", serializeTeamActivities(log, context))?.execute()?.body()
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
        mRealm = dbService.realmInstance
        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm.executeTransactionAsync { realm: Realm ->
            val activities = realm.where(RealmRating::class.java).equalTo("isUpdated", true).findAll()
            for (act in activities) {
                try {
                    if (act.userId?.startsWith("guest") == true) {
                        continue
                    }
                    val `object`: Response<JsonObject>? =
                        if (TextUtils.isEmpty(act._id)) {
                            apiInterface?.postDoc(Utilities.header, "application/json", Utilities.getUrl() + "/ratings", serializeRating(act))?.execute()
                        } else {
                            apiInterface?.putDoc(Utilities.header, "application/json", Utilities.getUrl() + "/ratings/" + act._id, serializeRating(act))?.execute()
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
        mRealm = dbService.realmInstance
        val apiInterface = client?.create(ApiInterface::class.java)
//        val userModel = UserProfileDbHandler(context).userModel
        mRealm.executeTransactionAsync { realm: Realm ->
            val activities = realm.where(RealmNews::class.java).findAll()
            for (act in activities) {
                try {
                    if (act.userId?.startsWith("guest") == true) {
                        continue
                    }
                    val `object` = serializeNews(act)
                    val image = act.imagesArray
                    val user = realm.where(RealmUserModel::class.java).equalTo("id", pref.getString("userId", "")).findFirst()
                    if (act.imageUrls != null) {
                        for (imageObject in act.imageUrls ?: emptyList()) {
                            val imgObject = Gson().fromJson(imageObject, JsonObject::class.java)
                            val ob = createImage(user, imgObject)
                            val response = apiInterface?.postDoc(Utilities.header, "application/json", Utilities.getUrl() + "/resources", ob)?.execute()?.body()
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
                    act.images = Gson().toJson(image)
                    `object`.add("images", image)
                    val newsUploadResponse: Response<JsonObject>? =
                        if (TextUtils.isEmpty(act._id)) {
                            apiInterface?.postDoc(Utilities.header, "application/json", Utilities.getUrl() + "/news", `object`)?.execute()
                        } else {
                            apiInterface?.putDoc(Utilities.header, "application/json", Utilities.getUrl() + "/news/" + act._id, `object`)?.execute()
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
        mRealm = dbService.realmInstance
        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm.executeTransactionAsync(Realm.Transaction { realm: Realm ->
            val logs: RealmResults<RealmApkLog> = realm.where(RealmApkLog::class.java).isNull("_rev").findAll()
            for (act in logs) {
                try {
                    val o = apiInterface?.postDoc(Utilities.header, "application/json", Utilities.getUrl() + "/apk_logs", serialize(act, context))?.execute()?.body()
                    if (o != null) {
                        act._rev = getString("rev", o)
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }, Realm.Transaction.OnSuccess {})
    }

    fun uploadSearchActivity() {
        mRealm = dbService.realmInstance
        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm.executeTransactionAsync { realm: Realm ->
            val logs: RealmResults<RealmSearchActivity> = realm.where(RealmSearchActivity::class.java).isEmpty("_rev").findAll()
            for (act in logs) {
                try {
                    val o = apiInterface?.postDoc(Utilities.header, "application/json", Utilities.getUrl() + "/search_activities", act.serialize())?.execute()?.body()
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
        mRealm = dbService.realmInstance
        val apiInterface = client?.create(ApiInterface::class.java)
        val db = if (type == "sync") {
            "admin_activities"
        } else {
            "resource_activities"
        }
        mRealm.executeTransactionAsync { realm: Realm ->
            val activities: RealmResults<RealmResourceActivity> =
                if (type == "sync") {
                    realm.where(RealmResourceActivity::class.java).isNull("_rev").equalTo("type", "sync").findAll()
                } else {
                    realm.where(RealmResourceActivity::class.java).isNull("_rev").notEqualTo("type", "sync").findAll()
                }
            for (act in activities) {
                try {
                    val `object` = apiInterface?.postDoc(Utilities.header, "application/json", Utilities.getUrl() + "/" + db, serializeResourceActivities(act))?.execute()?.body()
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
        mRealm = dbService.realmInstance
        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm.executeTransactionAsync { realm: Realm ->
            val activities: RealmResults<RealmCourseActivity> =
                realm.where(RealmCourseActivity::class.java).isNull("_rev")
                .notEqualTo("type", "sync").findAll()
            for (act in activities) {
                try {
                    val `object` = apiInterface?.postDoc(Utilities.header, "application/json", Utilities.getUrl() + "/course_activities", serializeSerialize(act))?.execute()?.body()
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
        mRealm = DatabaseService(context).realmInstance
        val apiInterface = client?.create(ApiInterface::class.java)

        mRealm.executeTransactionAsync { realm: Realm ->
            val meetups: List<RealmMeetup> = realm.where(RealmMeetup::class.java).findAll()

            for (meetup in meetups) {
                try {
                    val meetupJson = serialize(meetup)
                    val `object` = apiInterface?.postDoc(
                        Utilities.header, "application/json",
                        Utilities.getUrl() + "/meetups",
                        meetupJson
                    )?.execute()?.body()

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
