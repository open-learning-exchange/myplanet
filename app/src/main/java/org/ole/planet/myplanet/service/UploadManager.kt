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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.FileUploadService
import org.ole.planet.myplanet.datamanager.UploadApiInterface
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
import org.ole.planet.myplanet.model.RealmStepExam
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
    private val gson: Gson,
    private val uploadApiInterface: UploadApiInterface
) : FileUploadService() {

    private suspend fun uploadNewsActivities() = withContext(Dispatchers.IO) {
        databaseService.withRealm { realm ->
            val newsLog: List<RealmNewsLog> = realm.where(RealmNewsLog::class.java)
                .isNull("_id").or().isEmpty("_id")
                .findAll()

            newsLog.processInBatches { news ->
                try {
                    val response = uploadApiInterface.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/myplanet_activities", RealmNewsLog.serialize(news))
                    val `object` = response.body()

                    if (response.isSuccessful && `object` != null) {
                        realm.executeTransaction {
                            news._id = getString("id", `object`)
                            news._rev = getString("rev", `object`)
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun uploadActivities() {
        val model = databaseService.withRealm { realm ->
            realm.where(RealmUserModel::class.java)
                .equalTo("id", pref.getString("userId", ""))
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
        } ?: return

        if (model.isManager()) {
            return
        }

        try {
            uploadApiInterface.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/myplanet_activities", MyPlanet.getNormalMyPlanetActivities(MainApplication.context, pref, model))

            val responseObject = uploadApiInterface.getJsonObject(UrlUtils.header, "${UrlUtils.getUrl()}/myplanet_activities/${getAndroidId(MainApplication.context)}@${NetworkUtils.getUniqueIdentifier()}")
            var `object` = responseObject.body()

            if (responseObject.isSuccessful && `object` != null) {
                val usages = `object`.getAsJsonArray("usages")
                usages.addAll(MyPlanet.getTabletUsages(context))
                `object`.add("usages", usages)
            } else {
                `object` = MyPlanet.getMyPlanetActivities(context, pref, model)
            }

            uploadApiInterface.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/myplanet_activities", `object`)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun uploadExamResult() = withContext(Dispatchers.IO) {
        try {
            val submissionIds = databaseService.withRealm { realm ->
                realm.where(RealmSubmission::class.java).findAll()
                    .filter { (it.answers?.size ?: 0) > 0 && it.userId?.startsWith("guest") != true }
                    .mapNotNull { it.id }
            }

            submissionIds.chunked(BATCH_SIZE).forEach { batchIds ->
                val submissionsToUpload = databaseService.withRealm { realm ->
                    realm.where(RealmSubmission::class.java)
                        .`in`("id", batchIds.toTypedArray())
                        .findAll()
                        .map { sub ->
                            val serialized = RealmSubmission.serializeExamResult(realm, sub, context)
                            Triple(sub.id, serialized, sub._id)
                        }
                }

                for ((id, serialized, _id) in submissionsToUpload) {
                    try {
                        val response = if (TextUtils.isEmpty(_id)) {
                            uploadApiInterface.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/submissions", serialized)
                        } else {
                            uploadApiInterface.putDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/submissions/$_id", serialized)
                        }

                        val responseBody = response.body()
                        if (response.isSuccessful && responseBody != null && id != null) {
                            databaseService.withRealm { realm ->
                                realm.executeTransaction { transactionRealm ->
                                    transactionRealm.where(RealmSubmission::class.java).equalTo("id", id).findFirst()?.let { sub ->
                                        sub._id = getString("id", responseBody)
                                        sub._rev = getString("rev", responseBody)
                                    }
                                }
                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            uploadCourseProgress()
        } catch (e: Exception) {
            e.printStackTrace()
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

    suspend fun uploadAchievement() = withContext(Dispatchers.IO) {
        databaseService.withRealm { realm ->
            val list: List<RealmAchievement> = realm.where(RealmAchievement::class.java).findAll()
            list.processInBatches { sub ->
                try {
                    if (sub._id?.startsWith("guest") == true) {
                        return@processInBatches
                    }
                    // TODO: uploading achievement
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private suspend fun uploadCourseProgress() = withContext(Dispatchers.IO) {
        databaseService.withRealm { realm ->
            val data: List<RealmCourseProgress> = realm.where(RealmCourseProgress::class.java).isNull("_id").findAll()
            data.processInBatches { sub ->
                try {
                    if (sub.userId?.startsWith("guest") == true) {
                        return@processInBatches
                    }

                    val response = uploadApiInterface.postDoc(
                        UrlUtils.header,
                        "application/json",
                        "${UrlUtils.getUrl()}/courses_progress",
                        RealmCourseProgress.serializeProgress(sub)
                    )
                    val `object` = response.body()
                    if (response.isSuccessful && `object` != null) {
                        realm.executeTransaction {
                            sub._id = getString("id", `object`)
                            sub._rev = getString("rev", `object`)
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun uploadFeedback() {
        try {
            val feedbacksToUpload = withContext(Dispatchers.IO) {
                databaseService.withRealm { realm ->
                    realm.copyFromRealm(realm.where(RealmFeedback::class.java).findAll())
                }
            }

            if (feedbacksToUpload.isEmpty()) {
                return
            }

            feedbacksToUpload.forEach { feedback ->
                try {
                    val res = uploadApiInterface.postDoc(
                        UrlUtils.header,
                        "application/json",
                        "${UrlUtils.getUrl()}/feedback",
                        RealmFeedback.serializeFeedback(feedback)
                    )

                    val r = res.body()
                    if (res.isSuccessful && r != null) {
                        val revElement = r["rev"]
                        val idElement = r["id"]
                        if (revElement != null && idElement != null) {
                            withContext(Dispatchers.IO) {
                                databaseService.withRealm { realm ->
                                    realm.executeTransaction { transactionRealm ->
                                        val realmFeedback = transactionRealm.where(RealmFeedback::class.java).equalTo("id", feedback.id).findFirst()
                                        realmFeedback?.let {
                                            it._rev = revElement.asString
                                            it._id = idElement.asString
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun uploadSubmitPhotos() = withContext(Dispatchers.IO) {
        databaseService.withRealm { realm ->
            val data: List<RealmSubmitPhotos> = realm.where(RealmSubmitPhotos::class.java).equalTo("uploaded", false).findAll()
            data.processInBatches { sub ->
                try {
                    val response = uploadApiInterface.postDoc(
                        UrlUtils.header,
                        "application/json",
                        "${UrlUtils.getUrl()}/submissions",
                        RealmSubmitPhotos.serializeRealmSubmitPhotos(sub)
                    )
                    val `object` = response.body()
                    if (response.isSuccessful && `object` != null) {
                        val rev = getString("rev", `object`)
                        val id = getString("id", `object`)
                        realm.executeTransaction {
                            sub.uploaded = true
                            sub._rev = rev
                            sub._id = id
                        }
                        uploadAttachment(id, rev, sub, object : SuccessListener {
                            override fun onSuccess(success: String?) {}
                        })
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun uploadResource() = withContext(Dispatchers.IO) {
        databaseService.withRealm { realm ->
            val user = realm.where(RealmUserModel::class.java)
                .equalTo("id", pref.getString("userId", ""))
                .findFirst()

            val data: List<RealmMyLibrary> = realm.where(RealmMyLibrary::class.java)
                .isNull("_rev")
                .findAll()

            if (data.isEmpty()) {
                return@withRealm
            }

            data.processInBatches { sub ->
                try {
                    val response = uploadApiInterface.postDoc(
                        UrlUtils.header,
                        "application/json",
                        "${UrlUtils.getUrl()}/resources",
                        RealmMyLibrary.serialize(sub, user)
                    )
                    val `object` = response.body()

                    if (response.isSuccessful && `object` != null) {
                        val rev = getString("rev", `object`)
                        val id = getString("id", `object`)
                        realm.executeTransaction {
                            sub._rev = rev
                            sub._id = id
                        }
                        uploadAttachment(id, rev, sub, object : SuccessListener {
                            override fun onSuccess(success: String?) {}
                        })
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun uploadMyPersonal(personal: RealmMyPersonal) {
        if (!personal.isUploaded) {
            try {
                val response = uploadApiInterface.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/resources", RealmMyPersonal.serialize(personal, context))
                val `object` = response.body()
                if (response.isSuccessful && `object` != null) {
                    val rev = getString("rev", `object`)
                    val id = getString("id", `object`)
                    databaseService.withRealm { updateRealm ->
                        updateRealm.executeTransaction { transactionRealm ->
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
                        }
                        uploadAttachment(id, rev, personal, object : SuccessListener {
                            override fun onSuccess(success: String?) {}
                        })
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun uploadTeamTask() = withContext(Dispatchers.IO) {
        databaseService.withRealm { realm ->
            val list: List<RealmTeamTask> = realm.where(RealmTeamTask::class.java).findAll()
            val tasksToUpload = list.filter { task ->
                TextUtils.isEmpty(task._id) || task.isUpdated
            }

            tasksToUpload.processInBatches { task ->
                try {
                    val response = uploadApiInterface.postDoc(
                        UrlUtils.header,
                        "application/json",
                        "${UrlUtils.getUrl()}/tasks",
                        RealmTeamTask.serialize(realm, task)
                    )
                    val `object` = response.body()

                    if (response.isSuccessful && `object` != null) {
                        realm.executeTransaction {
                            val rev = getString("rev", `object`)
                            val id = getString("id", `object`)
                            task._rev = rev
                            task._id = id
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun uploadSubmissions() = withContext(Dispatchers.IO) {
        try {
            databaseService.withRealm { realm ->
                val list: List<RealmSubmission> = realm.where(RealmSubmission::class.java)
                    .equalTo("isUpdated", true).or().isEmpty("_id").findAll()

                list.processInBatches { submission ->
                    try {
                        val requestJson = RealmSubmission.serialize(realm, submission)
                        val response = if (TextUtils.isEmpty(submission._id)) {
                            uploadApiInterface.postDoc(
                                UrlUtils.header,
                                "application/json",
                                "${UrlUtils.getUrl()}/submissions",
                                requestJson
                            )
                        } else {
                            uploadApiInterface.putDoc(
                                UrlUtils.header,
                                "application/json",
                                "${UrlUtils.getUrl()}/submissions/${submission._id}",
                                requestJson
                            )
                        }

                        val jsonObject = response.body()
                        if (response.isSuccessful && jsonObject != null) {
                            realm.executeTransaction {
                                val rev = getString("rev", jsonObject)
                                val id = getString("id", jsonObject)
                                submission._rev = rev
                                submission._id = id
                                submission.isUpdated = false
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

    suspend fun uploadTeams() = withContext(Dispatchers.IO) {
        databaseService.withRealm { realm ->
            val teams: List<RealmMyTeam> =
                realm.where(RealmMyTeam::class.java).equalTo("updated", true).findAll()
            teams.processInBatches { team ->
                try {
                    val response = uploadApiInterface.postDoc(
                        UrlUtils.header,
                        "application/json",
                        "${UrlUtils.getUrl()}/teams",
                        RealmMyTeam.serialize(team)
                    )
                    val `object` = response.body()
                    if (response.isSuccessful && `object` != null) {
                        realm.executeTransaction {
                            team._rev = getString("rev", `object`)
                            team.updated = false
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun uploadUserActivities() {
        val model = databaseService.withRealm { realm ->
            realm.where(RealmUserModel::class.java)
                .equalTo("id", pref.getString("userId", ""))
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
        } ?: return

        if (model.isManager()) {
            return
        }

        try {
            databaseService.withRealm { realm ->
                val activities = realm.where(RealmOfflineActivity::class.java)
                    .isNull("_rev")
                    .equalTo("type", "login")
                    .findAll()

                activities.processInBatches { act ->
                    try {
                        if (act.userId?.startsWith("guest") == true) {
                            return@processInBatches
                        }

                        val response = uploadApiInterface.postDoc(
                            UrlUtils.header,
                            "application/json",
                            "${UrlUtils.getUrl()}/login_activities",
                            RealmOfflineActivity.serializeLoginActivities(act, context)
                        )
                        val `object` = response.body()
                        if (response.isSuccessful) {
                            realm.executeTransaction {
                                act.changeRev(`object`)
                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                uploadTeamActivities()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun uploadTeamActivities() = withContext(Dispatchers.IO) {
        databaseService.withRealm { realm ->
            val logs = realm.where(RealmTeamLog::class.java).isNull("_rev").findAll()
            logs.processInBatches { log ->
                try {
                    val response = uploadApiInterface.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/team_activities", RealmTeamLog.serializeTeamActivities(log, context))
                    val `object` = response.body()
                    if (response.isSuccessful && `object` != null) {
                        realm.executeTransaction {
                            log._id = getString("id", `object`)
                            log._rev = getString("rev", `object`)
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun uploadRating() = withContext(Dispatchers.IO) {
        databaseService.withRealm { realm ->
            val activities = realm.where(RealmRating::class.java).equalTo("isUpdated", true).findAll()
            activities.processInBatches { act ->
                try {
                    if (act.userId?.startsWith("guest") == true) {
                        return@processInBatches
                    }

                    val response = if (TextUtils.isEmpty(act._id)) {
                        uploadApiInterface.postDoc(
                            UrlUtils.header,
                            "application/json",
                            "${UrlUtils.getUrl()}/ratings",
                            RealmRating.serializeRating(act)
                        )
                    } else {
                        uploadApiInterface.putDoc(
                            UrlUtils.header,
                            "application/json",
                            "${UrlUtils.getUrl()}/ratings/" + act._id,
                            RealmRating.serializeRating(act)
                        )
                    }
                    val `object` = response.body()
                    if (response.isSuccessful && `object` != null) {
                        realm.executeTransaction {
                            act._id = getString("id", `object`)
                            act._rev = getString("rev", `object`)
                            act.isUpdated = false
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun uploadNews() {
        databaseService.withRealm { realm ->
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
                                val response = withContext(Dispatchers.IO) {
                                    uploadApiInterface.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/resources", ob)
                                }
                                val responseBody = response.body()

                                if (response.isSuccessful && responseBody != null) {
                                    val rev = getString("rev", responseBody)
                                    val id = getString("id", responseBody)
                                    val f = File(getString("imageUrl", imgObject))
                                    val name = FileUtils.getFileNameFromUrl(getString("imageUrl", imgObject))
                                    val format = "%s/resources/%s/%s"
                                    val connection = f.toURI().toURL().openConnection()
                                    val mimeType = connection.contentType
                                    val body = FileUtils.fullyReadFileToBytes(f)
                                        .toRequestBody("application/octet-stream".toMediaTypeOrNull())
                                    val url = String.format(format, UrlUtils.getUrl(), id, name)

                                    val res = withContext(Dispatchers.IO) {
                                        uploadApiInterface.uploadResource(getHeaderMap(mimeType, rev), url, body)
                                    }
                                    val attachment = res.body()

                                    if (res.isSuccessful && attachment != null) {
                                        val resourceObject = JsonObject()
                                        resourceObject.addProperty("resourceId", getString("id", attachment))
                                        resourceObject.addProperty("filename", getString("fileName", imgObject))
                                        val markdown = "![](resources/" + getString("id", attachment) + "/" + getString("fileName", imgObject) + ")"
                                        resourceObject.addProperty("markdown", markdown)

                                        var msg = getString("message", `object`)
                                        msg += "\n\n$markdown".trimIndent()
                                        `object`.addProperty("message", msg)
                                        image.add(resourceObject)
                                    }
                                }
                            }
                        }
                    }

                    act.images = gson.toJson(image)
                    `object`.add("images", image)

                    val newsUploadResponse = if (TextUtils.isEmpty(act._id)) {
                        withContext(Dispatchers.IO) {
                            uploadApiInterface.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/news", `object`)
                        }
                    } else {
                        withContext(Dispatchers.IO) {
                            uploadApiInterface.putDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/news/" + act._id, `object`)
                        }
                    }

                    val newsUploadBody = newsUploadResponse.body()
                    if (newsUploadResponse.isSuccessful && newsUploadBody != null) {
                        realm.executeTransaction {
                            act.imageUrls?.clear()
                            act._id = getString("id", newsUploadBody)
                            act._rev = getString("rev", newsUploadBody)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        uploadNewsActivities()
    }

    suspend fun uploadCrashLog() {
        try {
            databaseService.withRealm { realm ->
                uploadCrashLogData(realm)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun uploadCrashLogData(realm: Realm) {
        val logs: RealmResults<RealmApkLog> = realm.where(RealmApkLog::class.java).isNull("_rev").findAll()

        logs.processInBatches { act ->
            try {
                val response = withContext(Dispatchers.IO) {
                    uploadApiInterface.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/apk_logs", RealmApkLog.serialize(act, context))
                }
                val o = response.body()
                if (response.isSuccessful && o != null) {
                    realm.executeTransaction {
                        act._rev = getString("rev", o)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    suspend fun uploadSearchActivity() {
        databaseService.withRealm { realm ->
            val logs: RealmResults<RealmSearchActivity> = realm.where(RealmSearchActivity::class.java).isEmpty("_rev").findAll()
            logs.processInBatches { act ->
                try {
                    val response = withContext(Dispatchers.IO) {
                        uploadApiInterface.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/search_activities", act.serialize())
                    }
                    val o = response.body()
                    if (response.isSuccessful && o != null) {
                        realm.executeTransaction {
                            act._rev = getString("rev", o)
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun uploadResourceActivities(type: String) {
        val db = if (type == "sync") {
            "admin_activities"
        } else {
            "resource_activities"
        }

        databaseService.withRealm { realm ->
            val activities: RealmResults<RealmResourceActivity> =
                if (type == "sync") {
                    realm.where(RealmResourceActivity::class.java).isNull("_rev").equalTo("type", "sync").findAll()
                } else {
                    realm.where(RealmResourceActivity::class.java).isNull("_rev").notEqualTo("type", "sync").findAll()
                }
            activities.processInBatches { act ->
                try {
                    val response = withContext(Dispatchers.IO) {
                        uploadApiInterface.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/" + db, RealmResourceActivity.serializeResourceActivities(act))
                    }
                    val `object` = response.body()

                    if (response.isSuccessful && `object` != null) {
                        realm.executeTransaction {
                            act._rev = getString("rev", `object`)
                            act._id = getString("id", `object`)
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun uploadCourseActivities() {
        databaseService.withRealm { realm ->
            val activities: RealmResults<RealmCourseActivity> = realm.where(RealmCourseActivity::class.java).isNull("_rev").notEqualTo("type", "sync").findAll()
            activities.processInBatches { act ->
                try {
                    val response = withContext(Dispatchers.IO) {
                        uploadApiInterface.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/course_activities", RealmCourseActivity.serializeSerialize(act))
                    }
                    val `object` = response.body()

                    if (response.isSuccessful && `object` != null) {
                        realm.executeTransaction {
                            act._rev = getString("rev", `object`)
                            act._id = getString("id", `object`)
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun uploadMeetups() {
        databaseService.withRealm { realm ->
            val meetups: List<RealmMeetup> = realm.where(RealmMeetup::class.java).findAll()
            meetups.processInBatches { meetup ->
                try {
                    val meetupJson = RealmMeetup.serialize(meetup)
                    val response = withContext(Dispatchers.IO) {
                        uploadApiInterface.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/meetups", meetupJson)
                    }
                    val `object` = response.body()

                    if (response.isSuccessful && `object` != null) {
                        realm.executeTransaction {
                            meetup.meetupId = getString("id", `object`)
                            meetup.meetupIdRev = getString("rev", `object`)
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun uploadAdoptedSurveys() {
        databaseService.withRealm { realm ->
            val adoptedSurveys = realm.where(RealmStepExam::class.java)
                .isNotNull("sourceSurveyId")
                .isNull("_rev")
                .findAll()

            adoptedSurveys.processInBatches { survey ->
                try {
                    val surveyJson = RealmStepExam.serializeExam(realm, survey)
                    val response = withContext(Dispatchers.IO) {
                        uploadApiInterface.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/exams", surveyJson)
                    }
                    val `object` = response.body()
                    if (response.isSuccessful && `object` != null) {
                        realm.executeTransaction {
                            survey._rev = getString("rev", `object`)
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
