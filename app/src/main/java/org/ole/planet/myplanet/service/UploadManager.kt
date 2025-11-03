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
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.UrlUtils
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
    @AppPreferences private val pref: SharedPreferences,
    private val gson: Gson
) : FileUploadService() {

    private fun uploadNewsActivities() {
        val apiInterface = client?.create(ApiInterface::class.java)
        databaseService.withRealm { realm ->
            realm.executeTransactionAsync { transactionRealm: Realm ->
                val newsLog: List<RealmNewsLog> = transactionRealm.where(RealmNewsLog::class.java)
                    .isNull("_id").or().isEmpty("_id")
                    .findAll()

                newsLog.processInBatches { news ->
                        try {
                            val `object` = apiInterface?.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/myplanet_activities", RealmNewsLog.serialize(news))?.execute()?.body()

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

    }

    fun uploadActivities(listener: SuccessListener?) {
        val apiInterface = client?.create(ApiInterface::class.java)
        val model = databaseService.withRealm { realm ->
            realm.where(RealmUserModel::class.java)
                .equalTo("id", pref.getString("userId", ""))
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
        } ?: run {
            listener?.onSuccess("Cannot upload activities: user model is null")
            return
        }

        if (model.isManager()) {
            listener?.onSuccess("Skipping activities upload for manager")
            return
        }

        try {
            apiInterface?.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/myplanet_activities", MyPlanet.getNormalMyPlanetActivities(MainApplication.context, pref, model))?.enqueue(object : Callback<JsonObject?> {
                override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {}

                override fun onFailure(call: Call<JsonObject?>, t: Throwable) {}
            })

            apiInterface?.getJsonObject(UrlUtils.header, "${UrlUtils.getUrl()}/myplanet_activities/${getAndroidId(MainApplication.context)}@${NetworkUtils.getUniqueIdentifier()}")?.enqueue(object : Callback<JsonObject?> {
                override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                    var `object` = response.body()

                    if (`object` != null) {
                        val usages = `object`.getAsJsonArray("usages")
                        usages.addAll(MyPlanet.getTabletUsages(context))
                        `object`.add("usages", usages)
                    } else {
                        `object` = MyPlanet.getMyPlanetActivities(context, pref, model)
                    }

                    apiInterface.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/myplanet_activities", `object`).enqueue(object : Callback<JsonObject?> {
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
                    apiInterface.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/myplanet_activities", `object`).enqueue(object : Callback<JsonObject?> {
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
        val apiInterface = client.create(ApiInterface::class.java)

        try {
            data class SubmissionData(val id: String?, val serialized: JsonObject, val _id: String?, val _rev: String?)

            val submissionsToUpload = databaseService.withRealm { realm ->
                realm.where(RealmSubmission::class.java).findAll()
                    .filter { (it.answers?.size ?: 0) > 0 && it.userId?.startsWith("guest") != true }
                    .map { sub ->
                        val serialized = if (!TextUtils.isEmpty(sub._id)) {
                            RealmSubmission.serializeExamResult(realm, sub, context)
                        } else {
                            RealmSubmission.serializeExamResult(realm, sub, context)
                        }
                        SubmissionData(sub.id, serialized, sub._id, sub._rev)
                    }
            }

            var processedCount = 0
            var errorCount = 0

            submissionsToUpload.processInBatches { data ->
                try {
                    val response: JsonObject? = if (TextUtils.isEmpty(data._id)) {
                        apiInterface?.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/submissions", data.serialized)?.execute()?.body()
                    } else {
                        apiInterface?.putDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/submissions/${data._id}", data.serialized)?.execute()?.body()
                    }

                    if (response != null && data.id != null) {
                        databaseService.withRealm { realm ->
                            realm.executeTransaction { transactionRealm ->
                                transactionRealm.where(RealmSubmission::class.java).equalTo("id", data.id).findFirst()?.let { sub ->
                                    sub._id = getString("id", response)
                                    sub._rev = getString("rev", response)
                                }
                            }
                        }
                        processedCount++
                    } else {
                        errorCount++
                    }
                } catch (e: IOException) {
                    errorCount++
                    e.printStackTrace()
                } catch (e: Exception) {
                    errorCount++
                    e.printStackTrace()
                }
            }

            uploadCourseProgress()
            listener.onSuccess("Result sync completed successfully ($processedCount processed, $errorCount errors)")
        } catch (e: Exception) {
            e.printStackTrace()
            listener.onSuccess("Error during result sync: ${e.message}")
        }
    }

    private fun createImage(user: RealmUserModel?, imgObject: JsonObject?): JsonObject {
        val `object` = JsonObject()
        `object`.addProperty("title", getString("fileName", imgObject))
        `object`.addProperty("createdDate", Date().time)
        `object`.addProperty("filename", getString("fileName", imgObject))
        `object`.addProperty("private", true)
        user?.id?.let { `object`.addProperty("addedBy", it) }
        user?.parentCode?.let { `object`.addProperty("resideOn", it) }
        user?.planetCode?.let { `object`.addProperty("sourcePlanet", it) }
        val object1 = JsonObject()
        `object`.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
        `object`.addProperty("deviceName", NetworkUtils.getDeviceName())
        `object`.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(MainApplication.context))
        `object`.add("privateFor", object1)
        `object`.addProperty("mediaType", "image")
        return `object`
    }

    fun uploadAchievement() {
        databaseService.withRealm { realm ->
            realm.executeTransactionAsync { transactionRealm: Realm ->
                val list: List<RealmAchievement> =
                    transactionRealm.where(RealmAchievement::class.java).findAll()
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

    }

    private fun uploadCourseProgress() {
        val apiInterface = client.create(ApiInterface::class.java)
        databaseService.withRealm { realm ->
            realm.executeTransactionAsync { transactionRealm: Realm ->
                val data: List<RealmCourseProgress> =
                    transactionRealm.where(RealmCourseProgress::class.java).isNull("_id").findAll()
                var successCount = 0
                var skipCount = 0
                var errorCount = 0

                data.processInBatches { sub ->
                    try {
                        if (sub.userId?.startsWith("guest") == true) {
                            skipCount++
                            return@processInBatches
                        }

                        val `object` = apiInterface?.postDoc(
                            UrlUtils.header,
                            "application/json",
                            "${UrlUtils.getUrl()}/courses_progress",
                            RealmCourseProgress.serializeProgress(sub)
                        )?.execute()?.body()
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
    }

    fun uploadFeedback(listener: SuccessListener) {
        val apiInterface = client.create(ApiInterface::class.java)
        databaseService.withRealm { realm ->
            realm.executeTransactionAsync(Realm.Transaction { transactionRealm: Realm ->
                val feedbacks: List<RealmFeedback> =
                    transactionRealm.where(RealmFeedback::class.java).findAll()

                if (feedbacks.isEmpty()) {
                    return@Transaction
                }

                var successCount = 0
                var errorCount = 0

                feedbacks.processInBatches { feedback ->
                    try {
                        val res: Response<JsonObject>? = apiInterface?.postDoc(
                            UrlUtils.header,
                            "application/json",
                            "${UrlUtils.getUrl()}/feedback",
                            RealmFeedback.serializeFeedback(feedback)
                        )?.execute()

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
    }

    fun uploadSubmitPhotos(listener: SuccessListener?) {
        val apiInterface = client.create(ApiInterface::class.java)
        databaseService.withRealm { realm ->
            realm.executeTransactionAsync { transactionRealm: Realm ->
                val data: List<RealmSubmitPhotos> =
                    transactionRealm.where(RealmSubmitPhotos::class.java).equalTo("uploaded", false).findAll()
                data.processInBatches { sub ->
                    try {
                        val `object` = apiInterface?.postDoc(
                            UrlUtils.header,
                            "application/json",
                            "${UrlUtils.getUrl()}/submissions",
                            RealmSubmitPhotos.serializeRealmSubmitPhotos(sub)
                        )?.execute()?.body()
                        if (`object` != null) {
                            val rev = getString("rev", `object`)
                            val id = getString("id", `object`)
                            sub.uploaded = true
                            sub._rev = rev
                            sub._id = id
                            listener?.let { uploadAttachment(id, rev, sub, it) }
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
    }

    fun uploadResource(listener: SuccessListener?) {
        val apiInterface = client?.create(ApiInterface::class.java)

        databaseService.withRealm { realm ->
            realm.executeTransactionAsync({ transactionRealm: Realm ->
                val user = transactionRealm.where(RealmUserModel::class.java)
                    .equalTo("id", pref.getString("userId", ""))
                    .findFirst()

                val data: List<RealmMyLibrary> = transactionRealm.where(RealmMyLibrary::class.java)
                    .isNull("_rev")
                    .findAll()

                if (data.isEmpty()) {
                    return@executeTransactionAsync
                }

                data.processInBatches { sub ->
                    try {
                        val `object` = apiInterface?.postDoc(
                            UrlUtils.header,
                            "application/json",
                            "${UrlUtils.getUrl()}/resources",
                            RealmMyLibrary.serialize(sub, user)
                        )?.execute()?.body()

                        if (`object` != null) {
                            val rev = getString("rev", `object`)
                            val id = getString("id", `object`)
                            sub._rev = rev
                            sub._id = id
                            listener?.let { uploadAttachment(id, rev, sub, it) }
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
    }

    fun uploadMyPersonal(personal: RealmMyPersonal, listener: SuccessListener) {
        val apiInterface = client?.create(ApiInterface::class.java)

        if (!personal.isUploaded) {
            apiInterface?.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/resources", RealmMyPersonal.serialize(personal, context))?.enqueue(object : Callback<JsonObject?> {
                override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                    val `object` = response.body()
                    if (`object` != null) {
                        val rev = getString("rev", `object`)
                        val id = getString("id", `object`)
                        databaseService.withRealm { updateRealm ->
                            updateRealm.executeTransactionAsync({ transactionRealm ->
                                val managedPersonal = personal.id?.takeIf { it.isNotEmpty() }?.let { personalId ->
                                    transactionRealm.where(RealmMyPersonal::class.java)
                                        .equalTo("id", personalId)
                                        .findFirst()
                                } ?: personal._id?.takeIf { it.isNotEmpty() }?.let { existingId ->
                                    transactionRealm.where(RealmMyPersonal::class.java)
                                        .equalTo("_id", existingId)
                                        .findFirst()
                                }

                                managedPersonal?.let { realmPersonal ->
                                    realmPersonal.isUploaded = true
                                    realmPersonal._rev = rev
                                    realmPersonal._id = id
                                } ?: throw IllegalStateException("Personal resource not found")
                            }, {
                                uploadAttachment(id, rev, personal, listener)
                            }) { error ->
                                listener.onSuccess(
                                    "Error updating personal resource: ${error.message ?: "Unknown error"}"
                                )
                            }
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
        val apiInterface = client?.create(ApiInterface::class.java)
        databaseService.withRealm { realm ->
            realm.executeTransactionAsync { transactionRealm: Realm ->
                val list: List<RealmTeamTask> = transactionRealm.where(RealmTeamTask::class.java).findAll()
                val tasksToUpload = list.filter { task ->
                    TextUtils.isEmpty(task._id) || task.isUpdated
                }

                tasksToUpload.processInBatches { task ->
                    try {
                        val `object` = apiInterface?.postDoc(
                            UrlUtils.header,
                            "application/json",
                            "${UrlUtils.getUrl()}/tasks",
                            RealmTeamTask.serialize(transactionRealm, task)
                        )?.execute()?.body()

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
        val apiInterface = client?.create(ApiInterface::class.java)

        try {
            val hasLooper = Looper.myLooper() != null

            databaseService.withRealm { realm ->
                if (hasLooper) {
                    realm.executeTransactionAsync { transactionRealm: Realm ->
                        val list: List<RealmSubmission> = transactionRealm.where(RealmSubmission::class.java)
                            .equalTo("isUpdated", true).or().isEmpty("_id").findAll()

                        list.processInBatches { submission ->
                            try {
                                val requestJson = RealmSubmission.serialize(transactionRealm, submission)
                                val response = apiInterface?.postDoc(
                                    UrlUtils.header,
                                    "application/json",
                                    "${UrlUtils.getUrl()}/submissions",
                                    requestJson
                                )?.execute()

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
                } else {
                    realm.executeTransaction { transactionRealm: Realm ->
                        val list: List<RealmSubmission> = transactionRealm.where(RealmSubmission::class.java)
                            .equalTo("isUpdated", true).or().isEmpty("_id").findAll()

                        list.processInBatches { submission ->
                            try {
                                val requestJson = RealmSubmission.serialize(transactionRealm, submission)
                                val response = apiInterface?.postDoc(
                                    UrlUtils.header,
                                    "application/json",
                                    "${UrlUtils.getUrl()}/submissions",
                                    requestJson
                                )?.execute()

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
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun uploadTeams() {
        val apiInterface = client?.create(ApiInterface::class.java)
        databaseService.withRealm { realm ->
            realm.executeTransactionAsync { transactionRealm: Realm ->
                val teams: List<RealmMyTeam> =
                    transactionRealm.where(RealmMyTeam::class.java).equalTo("updated", true).findAll()
                teams.processInBatches { team ->
                    try {
                        val `object` = apiInterface?.postDoc(
                            UrlUtils.header,
                            "application/json",
                            "${UrlUtils.getUrl()}/teams",
                            RealmMyTeam.serialize(team)
                        )?.execute()?.body()
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
    }

    fun uploadUserActivities(listener: SuccessListener) {
        val apiInterface = client?.create(ApiInterface::class.java)
        val model = databaseService.withRealm { realm ->
            realm.where(RealmUserModel::class.java)
                .equalTo("id", pref.getString("userId", ""))
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
        } ?: run {
            listener.onSuccess("Cannot upload user activities: user model is null")
            return
        }

        if (model.isManager()) {
            listener.onSuccess("Skipping user activities upload for manager")
            return
        }

        databaseService.withRealm { realm ->
            realm.executeTransactionAsync({ transactionRealm: Realm ->
                val activities = transactionRealm.where(RealmOfflineActivity::class.java)
                    .isNull("_rev")
                    .equalTo("type", "login")
                    .findAll()

                activities.processInBatches { act ->
                    try {
                        if (act.userId?.startsWith("guest") == true) {
                            return@processInBatches
                        }

                        val `object` = apiInterface?.postDoc(
                            UrlUtils.header,
                            "application/json",
                            "${UrlUtils.getUrl()}/login_activities",
                            RealmOfflineActivity.serializeLoginActivities(act, context)
                        )?.execute()?.body()
                        act.changeRev(`object`)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                uploadTeamActivities(transactionRealm, apiInterface)
            }, {
                listener.onSuccess("User activities sync completed successfully")
            }) { e: Throwable ->
                e.printStackTrace()
                listener.onSuccess(e.message)
            }
        }
    }

    fun uploadTeamActivities(realm: Realm, apiInterface: ApiInterface?) {
        val logs = realm.where(RealmTeamLog::class.java).isNull("_rev").findAll()
        logs.processInBatches { log ->
                try {
                    val `object` = apiInterface?.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/team_activities", RealmTeamLog.serializeTeamActivities(log, context))?.execute()?.body()
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
        val apiInterface = client?.create(ApiInterface::class.java)

        databaseService.withRealm { realm ->
            realm.executeTransactionAsync { transactionRealm: Realm ->
                val activities = transactionRealm.where(RealmRating::class.java).equalTo("isUpdated", true).findAll()
                activities.processInBatches { act ->
                    try {
                        if (act.userId?.startsWith("guest") == true) {
                            return@processInBatches
                        }

                        val `object`: Response<JsonObject>? =
                            if (TextUtils.isEmpty(act._id)) {
                                apiInterface?.postDoc(
                                    UrlUtils.header,
                                    "application/json",
                                    "${UrlUtils.getUrl()}/ratings",
                                    RealmRating.serializeRating(act)
                                )?.execute()
                            } else {
                                apiInterface?.putDoc(
                                    UrlUtils.header,
                                    "application/json",
                                    "${UrlUtils.getUrl()}/ratings/" + act._id,
                                    RealmRating.serializeRating(act)
                                )?.execute()
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
    }

    fun uploadNews() {
        val apiInterface = client?.create(ApiInterface::class.java)

        databaseService.withRealm { realm ->
            realm.executeTransactionAsync { transactionRealm: Realm ->
            val activities = transactionRealm.where(RealmNews::class.java).findAll()
            activities.processInBatches { act ->
                    try {
                        if (act.userId?.startsWith("guest") == true) {
                            return@processInBatches
                        }

                        val `object` = RealmNews.serializeNews(act)
                        val image = act.imagesArray
                        val user = transactionRealm.where(RealmUserModel::class.java).equalTo("id", pref.getString("userId", "")).findFirst()

                        if (act.imageUrls != null && act.imageUrls?.isNotEmpty() == true) {
                            act.imageUrls?.chunked(5)?.forEach { imageChunk ->
                                imageChunk.forEach { imageObject ->
                                    val imgObject = gson.fromJson(imageObject, JsonObject::class.java)
                                    val ob = createImage(user, imgObject)
                                    val response = apiInterface?.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/resources", ob)?.execute()?.body()

                                    val rev = getString("rev", response)
                                    val id = getString("id", response)
                                    val f = File(getString("imageUrl", imgObject))
                                    val name = FileUtils.getFileNameFromUrl(getString("imageUrl", imgObject))
                                    val format = "%s/resources/%s/%s"
                                    val connection = f.toURI().toURL().openConnection()
                                    val mimeType = connection.contentType
                                    val body = FileUtils.fullyReadFileToBytes(f)
                                        .toRequestBody("application/octet-stream".toMediaTypeOrNull())
                                    val url = String.format(format, UrlUtils.getUrl(), id, name)

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
                                apiInterface?.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/news", `object`)?.execute()
                            } else {
                                apiInterface?.putDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/news/" + act._id, `object`)?.execute()
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
        }
        uploadNewsActivities()
    }

    fun uploadCrashLog() {
        val apiInterface = client?.create(ApiInterface::class.java)

        try {
            val hasLooper = Looper.myLooper() != null

            databaseService.withRealm { realm ->
                if (hasLooper) {
                    realm.executeTransactionAsync { transactionRealm: Realm ->
                        uploadCrashLogData(transactionRealm, apiInterface)
                    }
                } else {
                    realm.executeTransaction { transactionRealm: Realm ->
                        uploadCrashLogData(transactionRealm, apiInterface)
                    }
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
                    val o = apiInterface?.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/apk_logs", RealmApkLog.serialize(act, context))?.execute()?.body()

                    if (o != null) {
                        act._rev = getString("rev", o)
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
        }
    }

    fun uploadSearchActivity() {
        val apiInterface = client?.create(ApiInterface::class.java)
        databaseService.withRealm { realm ->
            realm.executeTransactionAsync { transactionRealm: Realm ->
            val logs: RealmResults<RealmSearchActivity> = transactionRealm.where(RealmSearchActivity::class.java).isEmpty("_rev").findAll()
            logs.processInBatches { act ->
                    try {
                        val o = apiInterface?.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/search_activities", act.serialize())?.execute()?.body()
                        if (o != null) {
                            act._rev = getString("rev", o)
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
            }
            }
        }

    }

    fun uploadResourceActivities(type: String) {
        val apiInterface = client?.create(ApiInterface::class.java)

        val db = if (type == "sync") {
            "admin_activities"
        } else {
            "resource_activities"
        }

        databaseService.withRealm { realm ->
            realm.executeTransactionAsync { transactionRealm: Realm ->
            val activities: RealmResults<RealmResourceActivity> =
                if (type == "sync") {
                    transactionRealm.where(RealmResourceActivity::class.java).isNull("_rev").equalTo("type", "sync").findAll()
                } else {
                    transactionRealm.where(RealmResourceActivity::class.java).isNull("_rev").notEqualTo("type", "sync").findAll()
                }
            activities.processInBatches { act ->
                    try {
                        val `object` = apiInterface?.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/" + db, RealmResourceActivity.serializeResourceActivities(act))?.execute()?.body()

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
    }

    fun uploadCourseActivities() {
        val apiInterface = client?.create(ApiInterface::class.java)

        databaseService.withRealm { realm ->
            realm.executeTransactionAsync { transactionRealm: Realm ->
            val activities: RealmResults<RealmCourseActivity> = transactionRealm.where(RealmCourseActivity::class.java).isNull("_rev").notEqualTo("type", "sync").findAll()
            activities.processInBatches { act ->
                    try {
                        val `object` = apiInterface?.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/course_activities", RealmCourseActivity.serializeSerialize(act))?.execute()?.body()

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
    }

    fun uploadMeetups() {
        val apiInterface = client?.create(ApiInterface::class.java)
        databaseService.withRealm { realm ->
            realm.executeTransactionAsync { transactionRealm: Realm ->
            val meetups: List<RealmMeetup> = transactionRealm.where(RealmMeetup::class.java).findAll()
            meetups.processInBatches { meetup ->
                    try {
                        val meetupJson = RealmMeetup.serialize(meetup)
                        val `object` = apiInterface?.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/meetups", meetupJson)?.execute()?.body()

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
}
