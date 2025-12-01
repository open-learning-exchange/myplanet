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

@Singleton
class UploadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseService: DatabaseService,
    @AppPreferences private val pref: SharedPreferences,
    private val gson: Gson
) : FileUploadService() {

    suspend fun uploadNewsActivities() {
        withContext(Dispatchers.IO) {
            val apiInterface = client?.create(ApiInterface::class.java) ?: return@withContext
            val newsLogs = databaseService.withRealm { realm ->
                val logs = realm.where(RealmNewsLog::class.java)
                    .isNull("_id").or().isEmpty("_id")
                    .findAll()
                realm.copyFromRealm(logs)
            }

            if (newsLogs.isEmpty()) return@withContext

            newsLogs.forEach { news ->
                try {
                    val response = apiInterface.postDocSuspend(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/myplanet_activities", RealmNewsLog.serialize(news))

                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        val id = getString("id", body)
                        val rev = getString("rev", body)

                        databaseService.withRealm { realm ->
                            realm.executeTransaction { r ->
                                val dbNews = r.where(RealmNewsLog::class.java).equalTo("id", news.id).findFirst()
                                dbNews?._id = id
                                dbNews?._rev = rev
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun uploadActivities() {
        withContext(Dispatchers.IO) {
            val apiInterface = client?.create(ApiInterface::class.java) ?: return@withContext
            val model = databaseService.withRealm { realm ->
                realm.where(RealmUserModel::class.java)
                    .equalTo("id", pref.getString("userId", ""))
                    .findFirst()
                    ?.let { realm.copyFromRealm(it) }
            } ?: return@withContext

            if (model.isManager()) return@withContext

            try {
                 apiInterface.postDocSuspend(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/myplanet_activities", MyPlanet.getNormalMyPlanetActivities(MainApplication.context, pref, model))

                 val response = apiInterface.getJsonObjectSuspended(UrlUtils.header, "${UrlUtils.getUrl()}/myplanet_activities/${getAndroidId(MainApplication.context)}@${NetworkUtils.getUniqueIdentifier()}")

                 var `object` = response.body()
                 if (`object` != null) {
                        val usages = `object`.getAsJsonArray("usages")
                        usages.addAll(MyPlanet.getTabletUsages(context))
                        `object`.add("usages", usages)
                 } else {
                        `object` = MyPlanet.getMyPlanetActivities(context, pref, model)
                 }

                 apiInterface.postDocSuspend(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/myplanet_activities", `object`)
            } catch (e: Exception) {
                 e.printStackTrace()
            }
        }
    }

    suspend fun uploadExamResult() {
        withContext(Dispatchers.IO) {
            val apiInterface = client.create(ApiInterface::class.java)
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
                                apiInterface.postDocSuspend(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/submissions", serialized).body()
                            } else {
                                apiInterface.putDocSuspend(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/submissions/$_id", serialized).body()
                            }

                            if (response != null && id != null) {
                                databaseService.withRealm { realm ->
                                    realm.executeTransaction { transactionRealm ->
                                        transactionRealm.where(RealmSubmission::class.java).equalTo("id", id).findFirst()?.let { sub ->
                                            sub._id = getString("id", response)
                                            sub._rev = getString("rev", response)
                                        }
                                    }
                                }
                            }
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

    suspend fun uploadAchievement() {
        // Original implementation was effectively empty
    }

    private suspend fun uploadCourseProgress() {
        withContext(Dispatchers.IO) {
            val apiInterface = client.create(ApiInterface::class.java)
            val data = databaseService.withRealm { realm ->
                val progress = realm.where(RealmCourseProgress::class.java).isNull("_id").findAll()
                realm.copyFromRealm(progress)
            }

            data.forEach { sub ->
                try {
                    if (sub.userId?.startsWith("guest") == true) return@forEach

                    val response = apiInterface.postDocSuspend(
                        UrlUtils.header,
                        "application/json",
                        "${UrlUtils.getUrl()}/courses_progress",
                        RealmCourseProgress.serializeProgress(sub)
                    )
                    val body = response.body()
                    if (response.isSuccessful && body != null) {
                        val id = getString("id", body)
                        val rev = getString("rev", body)
                        databaseService.withRealm { realm ->
                            realm.executeTransaction { r ->
                                val dbSub = r.where(RealmCourseProgress::class.java).equalTo("id", sub.id).findFirst()
                                dbSub?._id = id
                                dbSub?._rev = rev
                            }
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun uploadFeedback(): Boolean {
        // Keep original suspend implementation but ensure DB updates are safe
        val apiInterface = client.create(ApiInterface::class.java)
        var success = true
        try {
            val feedbacksToUpload = withContext(Dispatchers.IO) {
                databaseService.withRealm { realm ->
                    realm.copyFromRealm(realm.where(RealmFeedback::class.java).findAll())
                }
            }

            if (feedbacksToUpload.isEmpty()) {
                return true
            }

            feedbacksToUpload.forEach { feedback ->
                try {
                    val res = apiInterface.postDocSuspend(
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
                        } else {
                            success = false
                        }
                    } else {
                        success = false
                    }
                } catch (e: IOException) {
                    success = false
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            success = false
            e.printStackTrace()
        }
        return success
    }

    suspend fun uploadSubmitPhotos() {
        withContext(Dispatchers.IO) {
            val apiInterface = client.create(ApiInterface::class.java)
            val data = databaseService.withRealm { realm ->
                val photos = realm.where(RealmSubmitPhotos::class.java).equalTo("uploaded", false).findAll()
                realm.copyFromRealm(photos)
            }

            data.forEach { sub ->
                try {
                    val response = apiInterface.postDocSuspend(
                        UrlUtils.header,
                        "application/json",
                        "${UrlUtils.getUrl()}/submissions",
                        RealmSubmitPhotos.serializeRealmSubmitPhotos(sub)
                    )
                    val body = response.body()

                    if (response.isSuccessful && body != null) {
                        val rev = getString("rev", body)
                        val id = getString("id", body)

                        dbUpdate(sub.id) { s: RealmSubmitPhotos ->
                            s.uploaded = true
                            s._rev = rev
                            s._id = id
                        }
                        sub._id = id
                        sub._rev = rev
                        uploadAttachmentSuspend(id, rev, sub)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private inline fun <reified T : io.realm.RealmObject> dbUpdate(id: String?, crossinline block: (T) -> Unit) {
        if (id == null) return
        databaseService.withRealm { realm ->
            realm.executeTransaction { r ->
                val obj = r.where(T::class.java).equalTo("id", id).findFirst()
                obj?.let(block)
            }
        }
    }

    suspend fun uploadResource() {
        withContext(Dispatchers.IO) {
            val apiInterface = client?.create(ApiInterface::class.java) ?: return@withContext

            val (user, data) = databaseService.withRealm { realm ->
                val u = realm.where(RealmUserModel::class.java)
                    .equalTo("id", pref.getString("userId", ""))
                    .findFirst()
                    ?.let { realm.copyFromRealm(it) }

                val d = realm.where(RealmMyLibrary::class.java)
                    .isNull("_rev")
                    .findAll()
                val dList = realm.copyFromRealm(d)
                Pair(u, dList)
            }

            if (data.isEmpty()) return@withContext

            data.forEach { sub ->
                try {
                    val response = apiInterface.postDocSuspend(
                        UrlUtils.header,
                        "application/json",
                        "${UrlUtils.getUrl()}/resources",
                        RealmMyLibrary.serialize(sub, user)
                    )
                    val body = response.body()

                    if (response.isSuccessful && body != null) {
                        val rev = getString("rev", body)
                        val id = getString("id", body)

                        dbUpdate(sub.resourceId) { s: RealmMyLibrary ->
                            s._rev = rev
                            s._id = id
                        }
                        sub._id = id
                        sub._rev = rev
                        uploadAttachmentSuspend(id, rev, sub)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun uploadMyPersonal(personal: RealmMyPersonal) {
        withContext(Dispatchers.IO) {
             val apiInterface = client?.create(ApiInterface::class.java) ?: return@withContext
             if (!personal.isUploaded) {
                 try {
                     val response = apiInterface.postDocSuspend(
                         UrlUtils.header,
                         "application/json",
                         "${UrlUtils.getUrl()}/resources",
                         RealmMyPersonal.serialize(personal, context)
                     )
                     val body = response.body()

                     if (response.isSuccessful && body != null) {
                         val rev = getString("rev", body)
                         val id = getString("id", body)

                         databaseService.withRealm { realm ->
                             realm.executeTransaction { r ->
                                 val managed = if (!personal.id.isNullOrEmpty()) {
                                     r.where(RealmMyPersonal::class.java).equalTo("id", personal.id).findFirst()
                                 } else if (!personal._id.isNullOrEmpty()) {
                                     r.where(RealmMyPersonal::class.java).equalTo("_id", personal._id).findFirst()
                                 } else null

                                 managed?.isUploaded = true
                                 managed?._rev = rev
                                 managed?._id = id
                             }
                         }
                         personal._id = id
                         personal._rev = rev
                         uploadAttachmentSuspend(id, rev, personal)
                     }
                 } catch (e: Exception) {
                     e.printStackTrace()
                 }
             }
        }
    }

    suspend fun uploadTeamTask() {
        withContext(Dispatchers.IO) {
            val apiInterface = client?.create(ApiInterface::class.java) ?: return@withContext
            val tasks = databaseService.withRealm { realm ->
                val list = realm.where(RealmTeamTask::class.java).findAll()
                realm.copyFromRealm(list).filter { task ->
                    TextUtils.isEmpty(task._id) || task.isUpdated
                }
            }

            tasks.forEach { task ->
                try {
                    // RealmTeamTask.serialize needs realm
                    val serialized = databaseService.withRealm { r -> RealmTeamTask.serialize(r, task) }

                    val response = apiInterface.postDocSuspend(
                        UrlUtils.header,
                        "application/json",
                        "${UrlUtils.getUrl()}/tasks",
                        serialized
                    )
                    val body = response.body()

                    if (response.isSuccessful && body != null) {
                        val rev = getString("rev", body)
                        val id = getString("id", body)

                        dbUpdate(task.id) { t: RealmTeamTask ->
                            t._rev = rev
                            t._id = id
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun uploadSubmissions() {
        withContext(Dispatchers.IO) {
            val apiInterface = client?.create(ApiInterface::class.java) ?: return@withContext
            val submissions = databaseService.withRealm { realm ->
                val list = realm.where(RealmSubmission::class.java)
                    .equalTo("isUpdated", true).or().isEmpty("_id").findAll()
                realm.copyFromRealm(list)
            }

            submissions.forEach { submission ->
                try {
                    // Serialize needs realm
                    val requestJson = databaseService.withRealm { r -> RealmSubmission.serialize(r, submission) }

                    val response = if (TextUtils.isEmpty(submission._id)) {
                        apiInterface.postDocSuspend(
                            UrlUtils.header,
                            "application/json",
                            "${UrlUtils.getUrl()}/submissions",
                            requestJson
                        )
                    } else {
                        apiInterface.putDocSuspend(
                            UrlUtils.header,
                            "application/json",
                            "${UrlUtils.getUrl()}/submissions/${submission._id}",
                            requestJson
                        )
                    }

                    val body = response.body()
                    if (body != null) {
                        val rev = getString("rev", body)
                        val id = getString("id", body)

                        dbUpdate(submission.id) { s: RealmSubmission ->
                            s._rev = rev
                            s._id = id
                            s.isUpdated = false
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun uploadTeams() {
        withContext(Dispatchers.IO) {
            val apiInterface = client?.create(ApiInterface::class.java) ?: return@withContext
            val teams = databaseService.withRealm { realm ->
                val list = realm.where(RealmMyTeam::class.java).equalTo("updated", true).findAll()
                realm.copyFromRealm(list)
            }

            teams.forEach { team ->
                try {
                    val response = apiInterface.postDocSuspend(
                        UrlUtils.header,
                        "application/json",
                        "${UrlUtils.getUrl()}/teams",
                        RealmMyTeam.serialize(team)
                    )
                    val body = response.body()

                    if (response.isSuccessful && body != null) {
                        val rev = getString("rev", body)
                        dbUpdate(team.id) { t: RealmMyTeam ->
                            t._rev = rev
                            t.updated = false
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun uploadUserActivities() {
        withContext(Dispatchers.IO) {
            val apiInterface = client?.create(ApiInterface::class.java) ?: return@withContext
            val model = databaseService.withRealm { realm ->
                realm.where(RealmUserModel::class.java)
                    .equalTo("id", pref.getString("userId", ""))
                    .findFirst()
                    ?.let { realm.copyFromRealm(it) }
            } ?: return@withContext

            if (model.isManager()) return@withContext

            val activities = databaseService.withRealm { realm ->
                val list = realm.where(RealmOfflineActivity::class.java)
                    .isNull("_rev")
                    .equalTo("type", "login")
                    .findAll()
                realm.copyFromRealm(list)
            }

            activities.forEach { act ->
                try {
                    if (act.userId?.startsWith("guest") == true) return@forEach

                    val response = apiInterface.postDocSuspend(
                        UrlUtils.header,
                        "application/json",
                        "${UrlUtils.getUrl()}/login_activities",
                        RealmOfflineActivity.serializeLoginActivities(act, context)
                    )
                    val body = response.body()
                    if (body != null) {
                         dbUpdate(act.id) { a: RealmOfflineActivity ->
                             a.changeRev(body)
                         }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            uploadTeamActivities()
        }
    }

    suspend fun uploadTeamActivities() {
        withContext(Dispatchers.IO) {
            val apiInterface = client?.create(ApiInterface::class.java) ?: return@withContext
            val logs = databaseService.withRealm { realm ->
                val list = realm.where(RealmTeamLog::class.java).isNull("_rev").findAll()
                realm.copyFromRealm(list)
            }

            logs.forEach { log ->
                try {
                    val response = apiInterface.postDocSuspend(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/team_activities", RealmTeamLog.serializeTeamActivities(log, context))
                    val body = response.body()

                    if (body != null) {
                        val id = getString("id", body)
                        val rev = getString("rev", body)
                        dbUpdate(log.id) { l: RealmTeamLog ->
                            l._id = id
                            l._rev = rev
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun uploadRating() {
        withContext(Dispatchers.IO) {
            val apiInterface = client?.create(ApiInterface::class.java) ?: return@withContext
            val activities = databaseService.withRealm { realm ->
                val list = realm.where(RealmRating::class.java).equalTo("isUpdated", true).findAll()
                realm.copyFromRealm(list)
            }

            activities.forEach { act ->
                try {
                    if (act.userId?.startsWith("guest") == true) return@forEach

                    val response = if (TextUtils.isEmpty(act._id)) {
                        apiInterface.postDocSuspend(
                            UrlUtils.header,
                            "application/json",
                            "${UrlUtils.getUrl()}/ratings",
                            RealmRating.serializeRating(act)
                        )
                    } else {
                        apiInterface.putDocSuspend(
                            UrlUtils.header,
                            "application/json",
                            "${UrlUtils.getUrl()}/ratings/" + act._id,
                            RealmRating.serializeRating(act)
                        )
                    }

                    val body = response.body()
                    if (body != null) {
                        val id = getString("id", body)
                        val rev = getString("rev", body)
                        dbUpdate(act.id) { r: RealmRating ->
                            r._id = id
                            r._rev = rev
                            r.isUpdated = false
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun uploadNews() {
        withContext(Dispatchers.IO) {
            val apiInterface = client?.create(ApiInterface::class.java) ?: return@withContext

            val activities = databaseService.withRealm { realm ->
                realm.copyFromRealm(realm.where(RealmNews::class.java).findAll())
            }

            val user = databaseService.withRealm { realm ->
                realm.where(RealmUserModel::class.java).equalTo("id", pref.getString("userId", "")).findFirst()?.let { realm.copyFromRealm(it) }
            }

            activities.forEach { act ->
                try {
                    if (act.userId?.startsWith("guest") == true) return@forEach

                    val `object` = RealmNews.serializeNews(act)
                    val image = act.imagesArray

                    if (act.imageUrls != null && act.imageUrls?.isNotEmpty() == true) {
                        act.imageUrls?.chunked(5)?.forEach { imageChunk ->
                            imageChunk.forEach { imageObject ->
                                val imgObject = gson.fromJson(imageObject, JsonObject::class.java)
                                val ob = createImage(user, imgObject)
                                val response = apiInterface.postDocSuspend(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/resources", ob).body()

                                val rev = getString("rev", response)
                                val id = getString("id", response)
                                val f = File(getString("imageUrl", imgObject))
                                val name = FileUtils.getFileNameFromUrl(getString("imageUrl", imgObject))
                                val format = "%s/resources/%s/%s"

                                // uploadResourceSuspend needed here
                                val connection = f.toURI().toURL().openConnection()
                                val mimeType = connection.contentType
                                val body = FileUtils.fullyReadFileToBytes(f)
                                    .toRequestBody("application/octet-stream".toMediaTypeOrNull())
                                val url = String.format(format, UrlUtils.getUrl(), id, name)

                                val res = apiInterface.uploadResourceSuspend(getHeaderMap(mimeType, rev), url, body)
                                val attachment = res.body()

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

                    val newsUploadResponse = if (TextUtils.isEmpty(act._id)) {
                        apiInterface.postDocSuspend(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/news", `object`)
                    } else {
                        apiInterface.putDocSuspend(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/news/" + act._id, `object`)
                    }

                    val body = newsUploadResponse.body()
                    if (body != null) {
                         val id = getString("id", body)
                         val rev = getString("rev", body)

                         dbUpdate(act.id) { n: RealmNews ->
                             n.imageUrls?.clear()
                             n._id = id
                             n._rev = rev
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
        withContext(Dispatchers.IO) {
            val apiInterface = client?.create(ApiInterface::class.java) ?: return@withContext
            val logs = databaseService.withRealm { realm ->
                val list = realm.where(RealmApkLog::class.java).isNull("_rev").findAll()
                realm.copyFromRealm(list)
            }

            logs.forEach { act ->
                try {
                    val response = apiInterface.postDocSuspend(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/apk_logs", RealmApkLog.serialize(act, context))
                    val body = response.body()
                    if (body != null) {
                        val rev = getString("rev", body)
                        dbUpdate(act.id) { a: RealmApkLog ->
                            a._rev = rev
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun uploadSearchActivity() {
        withContext(Dispatchers.IO) {
            val apiInterface = client?.create(ApiInterface::class.java) ?: return@withContext
            val logs = databaseService.withRealm { realm ->
                val list = realm.where(RealmSearchActivity::class.java).isEmpty("_rev").findAll()
                realm.copyFromRealm(list)
            }

            logs.forEach { act ->
                try {
                    val response = apiInterface.postDocSuspend(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/search_activities", act.serialize())
                    val body = response.body()
                    if (body != null) {
                        val rev = getString("rev", body)
                        dbUpdate(act.id) { a: RealmSearchActivity ->
                            a._rev = rev
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun uploadResourceActivities(type: String) {
        withContext(Dispatchers.IO) {
            val apiInterface = client?.create(ApiInterface::class.java) ?: return@withContext
            val db = if (type == "sync") "admin_activities" else "resource_activities"

            val activities = databaseService.withRealm { realm ->
                val list = if (type == "sync") {
                    realm.where(RealmResourceActivity::class.java).isNull("_rev").equalTo("type", "sync").findAll()
                } else {
                    realm.where(RealmResourceActivity::class.java).isNull("_rev").notEqualTo("type", "sync").findAll()
                }
                realm.copyFromRealm(list)
            }

            activities.forEach { act ->
                try {
                    val response = apiInterface.postDocSuspend(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/$db", RealmResourceActivity.serializeResourceActivities(act))
                    val body = response.body()
                    if (body != null) {
                        val rev = getString("rev", body)
                        val id = getString("id", body)
                        dbUpdate(act.id) { a: RealmResourceActivity ->
                            a._rev = rev
                            a._id = id
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun uploadCourseActivities() {
        withContext(Dispatchers.IO) {
            val apiInterface = client?.create(ApiInterface::class.java) ?: return@withContext
            val activities = databaseService.withRealm { realm ->
                val list = realm.where(RealmCourseActivity::class.java).isNull("_rev").notEqualTo("type", "sync").findAll()
                realm.copyFromRealm(list)
            }

            activities.forEach { act ->
                try {
                    val response = apiInterface.postDocSuspend(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/course_activities", RealmCourseActivity.serializeSerialize(act))
                    val body = response.body()
                    if (body != null) {
                         val rev = getString("rev", body)
                         val id = getString("id", body)
                         dbUpdate(act.id) { a: RealmCourseActivity ->
                             a._rev = rev
                             a._id = id
                         }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun uploadMeetups() {
        withContext(Dispatchers.IO) {
            val apiInterface = client?.create(ApiInterface::class.java) ?: return@withContext
            val meetups = databaseService.withRealm { realm ->
                val list = realm.where(RealmMeetup::class.java).findAll()
                realm.copyFromRealm(list)
            }

            meetups.forEach { meetup ->
                try {
                    val meetupJson = RealmMeetup.serialize(meetup)
                    val response = apiInterface.postDocSuspend(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/meetups", meetupJson)
                    val body = response.body()
                    if (body != null) {
                        val id = getString("id", body)
                        val rev = getString("rev", body)
                        dbUpdate(meetup.id) { m: RealmMeetup ->
                            m.meetupId = id
                            m.meetupIdRev = rev
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun uploadAdoptedSurveys() {
        withContext(Dispatchers.IO) {
            val apiInterface = client.create(ApiInterface::class.java)
            val surveys = databaseService.withRealm { realm ->
                val list = realm.where(RealmStepExam::class.java)
                    .isNotNull("sourceSurveyId")
                    .isNull("_rev")
                    .findAll()
                realm.copyFromRealm(list)
            }

            surveys.forEach { survey ->
                try {
                    val surveyJson = databaseService.withRealm { r -> RealmStepExam.serializeExam(r, survey) }
                    val response = apiInterface.postDocSuspend(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/exams", surveyJson)
                    val body = response.body()
                    if (body != null) {
                        val rev = getString("rev", body)
                        dbUpdate(survey.id) { s: RealmStepExam ->
                            s._rev = rev
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
