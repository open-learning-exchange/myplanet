package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.data.ApiClient.client
import org.ole.planet.myplanet.data.ApiInterface
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.model.RealmAchievement
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyPersonal
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmSubmitPhotos
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.service.upload.UploadConfigs
import org.ole.planet.myplanet.service.upload.UploadCoordinator
import org.ole.planet.myplanet.service.upload.UploadResult
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.UrlUtils
import org.ole.planet.myplanet.utilities.VersionUtils.getAndroidId
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

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
    private val submissionsRepository: SubmissionsRepository,
    @AppPreferences private val pref: SharedPreferences,
    private val gson: Gson,
    private val uploadCoordinator: UploadCoordinator
) : FileUploadService() {

    private suspend fun uploadNewsActivities() {
        uploadCoordinator.upload(UploadConfigs.NewsActivities)
    }

    fun uploadActivities(listener: SuccessListener?) {
        val apiInterface = client.create(ApiInterface::class.java)
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
            apiInterface.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/myplanet_activities", MyPlanet.getNormalMyPlanetActivities(MainApplication.context, pref, model)).enqueue(object : Callback<JsonObject?> {
                override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {}

                override fun onFailure(call: Call<JsonObject?>, t: Throwable) {}
            })

            apiInterface.getJsonObject(UrlUtils.header, "${UrlUtils.getUrl()}/myplanet_activities/${getAndroidId(MainApplication.context)}@${NetworkUtils.getUniqueIdentifier()}")
                .enqueue(object : Callback<JsonObject?> {
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

    suspend fun uploadExamResult(listener: SuccessListener) {
        withContext(Dispatchers.IO) {
            try {
                val result = uploadCoordinator.upload(UploadConfigs.ExamResults)

                val message = when (result) {
                    is UploadResult.Success -> "Result sync completed successfully (${result.data} processed, 0 errors)"
                    is UploadResult.PartialSuccess -> "Result sync completed with issues (${result.succeeded.size} processed, ${result.failed.size} errors)"
                    is UploadResult.Failure -> "Result sync failed: ${result.errors.size} errors"
                    is UploadResult.Empty -> "No exam results to upload"
                }

                uploadCourseProgress()
                withContext(Dispatchers.Main) {
                    listener.onSuccess(message)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    listener.onSuccess("Error during result sync: ${e.message}")
                }
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
        databaseService.executeTransactionAsync { transactionRealm ->
            val list: List<RealmAchievement> = transactionRealm.where(RealmAchievement::class.java).findAll()
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

    private suspend fun uploadCourseProgress() {
        uploadCoordinator.upload(UploadConfigs.CourseProgress)
    }

    suspend fun uploadFeedback(): Boolean {
        return when (val result = uploadCoordinator.upload(UploadConfigs.Feedback)) {
            is UploadResult.Success -> true
            is UploadResult.PartialSuccess -> result.failed.isEmpty()
            is UploadResult.Failure -> false
            is UploadResult.Empty -> true
        }
    }

    suspend fun uploadSubmitPhotos(listener: SuccessListener?) {
        val apiInterface = client.create(ApiInterface::class.java)

        data class PhotoData(
            val photoId: String?,
            val serialized: JsonObject
        )

        val photosToUpload = databaseService.withRealm { realm ->
            val data = realm.where(RealmSubmitPhotos::class.java).equalTo("uploaded", false).findAll()

            if (data.isEmpty()) {
                emptyList()
            } else {
                data.map { photo ->
                    val copiedPhoto = realm.copyFromRealm(photo)
                    PhotoData(
                        photoId = copiedPhoto.id, serialized = RealmSubmitPhotos.serializeRealmSubmitPhotos(copiedPhoto)
                    )
                }
            }
        }

        if (photosToUpload.isEmpty()) {
            listener?.onSuccess("No photos to upload")
            return
        }

        withContext(Dispatchers.IO) {
            photosToUpload.chunked(BATCH_SIZE).forEach { batch ->
                batch.forEach { photoData ->
                    try {
                        val `object` = apiInterface.postDoc(UrlUtils.header, "application/json",
                            "${UrlUtils.getUrl()}/submissions", photoData.serialized
                        ).execute().body()

                        if (`object` != null) {
                            val rev = getString("rev", `object`)
                            val id = getString("id", `object`)

                            databaseService.executeTransactionAsync { transactionRealm ->
                                transactionRealm.where(RealmSubmitPhotos::class.java)
                                    .equalTo("id", photoData.photoId)
                                    .findFirst()?.let { sub ->
                                        sub.uploaded = true
                                        sub._rev = rev
                                        sub._id = id
                                    }
                            }

                            listener?.let {
                                val photo = databaseService.withRealm { realm ->
                                    realm.where(RealmSubmitPhotos::class.java)
                                        .equalTo("id", photoData.photoId).findFirst()
                                        ?.let { realm.copyFromRealm(it) }
                                }
                                photo?.let { uploadAttachment(id, rev, it, listener) }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    suspend fun uploadResource(listener: SuccessListener?) {
        val apiInterface = client.create(ApiInterface::class.java)

        try {
            data class ResourceData(
                val libraryId: String?,
                val serialized: JsonObject
            )

            val user = databaseService.withRealm { realm ->
                realm.where(RealmUserModel::class.java)
                    .equalTo("id", pref.getString("userId", "")).findFirst()
                    ?.let { realm.copyFromRealm(it) }
            }

            val resourcesToUpload = databaseService.withRealm { realm ->
                val data = realm.where(RealmMyLibrary::class.java).isNull("_rev").findAll()

                if (data.isEmpty()) {
                    emptyList()
                } else {
                    data.map { library ->
                        val copiedLibrary = realm.copyFromRealm(library)
                        ResourceData(
                            libraryId = copiedLibrary.id,
                            serialized = RealmMyLibrary.serialize(copiedLibrary, user)
                        )
                    }
                }
            }

            if (resourcesToUpload.isEmpty()) {
                listener?.onSuccess("No resources to upload")
                return
            }

            withContext(Dispatchers.IO) {
                resourcesToUpload.chunked(BATCH_SIZE).forEach { batch ->
                    batch.forEach { resourceData ->
                        try {
                            val `object` = apiInterface.postDoc(UrlUtils.header, "application/json",
                                "${UrlUtils.getUrl()}/resources", resourceData.serialized
                            ).execute().body()

                            if (`object` != null) {
                                val rev = getString("rev", `object`)
                                val id = getString("id", `object`)

                                databaseService.executeTransactionAsync { transactionRealm ->
                                    transactionRealm.where(RealmMyLibrary::class.java)
                                        .equalTo("id", resourceData.libraryId)
                                        .findFirst()?.let { sub ->
                                            sub._rev = rev
                                            sub._id = id
                                        }
                                }

                                listener?.let {
                                    val library = databaseService.withRealm { realm ->
                                        realm.where(RealmMyLibrary::class.java)
                                            .equalTo("id", resourceData.libraryId).findFirst()
                                            ?.let { realm.copyFromRealm(it) }
                                    }
                                    library?.let { uploadAttachment(id, rev, it, listener) }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            listener?.onSuccess("Resource upload failed: ${e.message}")
        }
    }

    suspend fun uploadMyPersonal(personal: RealmMyPersonal): String {
        val apiInterface = client.create(ApiInterface::class.java)

        if (!personal.isUploaded) {
            return withContext(Dispatchers.IO) {
                try {
                    val response = apiInterface.postDoc(UrlUtils.header, "application/json",
                        "${UrlUtils.getUrl()}/resources", RealmMyPersonal.serialize(personal, context)
                    ).execute()

                    val `object` = response.body()
                    if (`object` != null) {
                        val rev = getString("rev", `object`)
                        val id = getString("id", `object`)

                        databaseService.executeTransactionAsync { transactionRealm ->
                            val managedPersonal = personal.id?.takeIf { it.isNotEmpty() }?.let { personalId ->
                                transactionRealm.where(RealmMyPersonal::class.java)
                                    .equalTo("id", personalId).findFirst()
                            } ?: personal._id?.takeIf { it.isNotEmpty() }?.let { existingId ->
                                transactionRealm.where(RealmMyPersonal::class.java)
                                    .equalTo("_id", existingId).findFirst()
                            }

                            managedPersonal?.let { realmPersonal ->
                                realmPersonal.isUploaded = true
                                realmPersonal._rev = rev
                                realmPersonal._id = id
                            } ?: throw IllegalStateException("Personal resource not found")
                        }

                        uploadAttachment(id, rev, personal) { }
                        "Personal resource uploaded successfully"
                    } else {
                        "Failed to upload personal resource: No response"
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    "Unable to upload resource: ${e.message}"
                }
            }
        } else {
            return "Resource already uploaded"
        }
    }

    suspend fun uploadTeamTask() {
        uploadCoordinator.upload(UploadConfigs.TeamTask)
    }

    suspend fun uploadSubmissions(buttonClickTime: Long = 0L) {
        Log.d("UploadManager", "uploadSubmissions called with buttonClickTime: $buttonClickTime")
        val startTime = if (buttonClickTime > 0) buttonClickTime else System.currentTimeMillis()

        if (buttonClickTime > 0) {
            Log.d("UploadManager", "Mini survey sync timer started from button click at: $startTime")
        } else {
            Log.d("UploadManager", "Mini survey sync started at: $startTime (buttonClickTime was $buttonClickTime)")
        }

        try {
            val result = uploadCoordinator.upload(UploadConfigs.Submissions)

            Log.d("UploadManager", when (result) {
                is UploadResult.Success -> "Uploaded ${result.data} submissions successfully"
                is UploadResult.PartialSuccess -> "Partial success: ${result.succeeded.size} succeeded, ${result.failed.size} failed"
                is UploadResult.Failure -> "Upload failed: ${result.errors.size} errors"
                is UploadResult.Empty -> "No submissions to upload"
            })
        } catch (e: Exception) {
            Log.e("UploadManager", "Error uploading submissions", e)
        } finally {
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            Log.d("UploadManager", "Mini survey sync completed at: $endTime")
            Log.d("UploadManager", "Total time from button click to sync completion: ${duration}ms (${duration / 1000.0}s)")
        }
    }

    suspend fun uploadTeams() {
        val apiInterface = client.create(ApiInterface::class.java)

        data class TeamData(
            val teamId: String?,
            val serialized: JsonObject
        )

        val teamsToUpload = databaseService.withRealm { realm ->
            val teams = realm.where(RealmMyTeam::class.java)
                .equalTo("updated", true).findAll()

            teams.map { team ->
                val copiedTeam = realm.copyFromRealm(team)
                TeamData(
                    teamId = copiedTeam._id,
                    serialized = RealmMyTeam.serialize(copiedTeam)
                )
            }
        }

        withContext(Dispatchers.IO) {
            teamsToUpload.chunked(BATCH_SIZE).forEach { batch ->
                batch.forEach { teamData ->
                    try {
                        val `object` = apiInterface.postDoc(UrlUtils.header, "application/json",
                            "${UrlUtils.getUrl()}/teams", teamData.serialized).execute().body()

                        if (`object` != null) {
                            val rev = getString("rev", `object`)

                            databaseService.executeTransactionAsync { transactionRealm ->
                                transactionRealm.where(RealmMyTeam::class.java)
                                    .equalTo("_id", teamData.teamId)
                                    .findFirst()?.let { team ->
                                        team._rev = rev
                                        team.updated = false
                                    }
                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    suspend fun uploadUserActivities(listener: SuccessListener) {
        val apiInterface = client.create(ApiInterface::class.java)
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

        try {
            data class ActivityData(
                val activityId: String?,
                val userId: String?,
                val serialized: JsonObject
            )

            val activitiesToUpload = databaseService.withRealm { realm ->
                val activities = realm.where(RealmOfflineActivity::class.java)
                    .isNull("_rev").equalTo("type", "login").findAll()

                activities.mapNotNull { activity ->
                    if (activity.userId?.startsWith("guest") == true) {
                        null
                    } else {
                        val copiedActivity = realm.copyFromRealm(activity)
                        ActivityData(
                            activityId = copiedActivity._id,
                            userId = copiedActivity.userId,
                            serialized = RealmOfflineActivity.serializeLoginActivities(copiedActivity, context)
                        )
                    }
                }
            }

            activitiesToUpload.chunked(BATCH_SIZE).forEach { batch ->
                batch.forEach { activityData ->
                    try {
                        val `object` = apiInterface.postDoc(UrlUtils.header, "application/json",
                            "${UrlUtils.getUrl()}/login_activities", activityData.serialized
                        ).execute().body()

                        databaseService.executeTransactionAsync { transactionRealm ->
                            transactionRealm.where(RealmOfflineActivity::class.java)
                                .equalTo("_id", activityData.activityId)
                                .findFirst()?.changeRev(`object`)
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }

            uploadTeamActivitiesRefactored()

            listener.onSuccess("User activities sync completed successfully")
        } catch (e: Exception) {
            e.printStackTrace()
            listener.onSuccess("Failed to upload user activities: ${e.message}")
        }
    }

    private suspend fun uploadTeamActivitiesRefactored() {
        uploadCoordinator.upload(UploadConfigs.TeamActivitiesRefactored)
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

    suspend fun uploadRating() {
        uploadCoordinator.upload(UploadConfigs.Rating)
    }

    suspend fun uploadNews() {
        // Note: uploadNews has unique logic that requires uploading images BEFORE the news document,
        // then modifying the serialized JSON based on image upload responses. This doesn't fit the
        // standard UploadCoordinator pattern, so we handle it with custom logic but still use
        // the coordinator for the core upload/update flow where possible.

        val apiInterface = client.create(ApiInterface::class.java)
        val user = databaseService.withRealm { realm ->
            realm.where(RealmUserModel::class.java)
                .equalTo("id", pref.getString("userId", ""))
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
        }

        val newsItems = databaseService.withRealm { realm ->
            realm.where(RealmNews::class.java)
                .findAll()
                .mapNotNull { news ->
                    if (news.userId?.startsWith("guest") == true) null
                    else realm.copyFromRealm(news)
                }
        }

        withContext(Dispatchers.IO) {
            newsItems.chunked(BATCH_SIZE).forEach { batch ->
                batch.forEach { news ->
                    try {
                        // Upload images first and collect metadata
                        val imagesArray = com.google.gson.JsonArray()
                        var messageWithImages = news.message ?: ""

                        news.imageUrls?.forEach { imageUrl ->
                            val imgObject = gson.fromJson(imageUrl, JsonObject::class.java)

                            // Create image resource document
                            val imageDoc = createImage(user, imgObject)
                            val imageResponse = apiInterface.postDoc(
                                UrlUtils.header,
                                "application/json",
                                "${UrlUtils.getUrl()}/resources",
                                imageDoc
                            ).execute().body()

                            val resourceId = getString("id", imageResponse)
                            val resourceRev = getString("rev", imageResponse)

                            // Upload image file as attachment
                            val imageFile = File(getString("imageUrl", imgObject))
                            val fileName = FileUtils.getFileNameFromUrl(getString("imageUrl", imgObject))
                            val mimeType = imageFile.toURI().toURL().openConnection().contentType
                            val fileBody = FileUtils.fullyReadFileToBytes(imageFile)
                                .toRequestBody("application/octet-stream".toMediaTypeOrNull())

                            apiInterface.uploadResource(
                                getHeaderMap(mimeType, resourceRev),
                                "${UrlUtils.getUrl()}/resources/$resourceId/$fileName",
                                fileBody
                            ).execute()

                            // Build image metadata and markdown
                            val resourceObject = JsonObject()
                            resourceObject.addProperty("resourceId", resourceId)
                            resourceObject.addProperty("filename", fileName)
                            val markdown = "![](resources/$resourceId/$fileName)"
                            resourceObject.addProperty("markdown", markdown)
                            imagesArray.add(resourceObject)

                            // Append markdown to message
                            messageWithImages += "\n$markdown"
                        }

                        // Serialize news with updated message and images
                        val newsJson = RealmNews.serializeNews(news)
                        newsJson.addProperty("message", messageWithImages)
                        newsJson.add("images", imagesArray)

                        // Upload news document (POST or PUT)
                        val newsResponse = if (TextUtils.isEmpty(news._id)) {
                            apiInterface.postDoc(
                                UrlUtils.header,
                                "application/json",
                                "${UrlUtils.getUrl()}/news",
                                newsJson
                            ).execute()
                        } else {
                            apiInterface.putDoc(
                                UrlUtils.header,
                                "application/json",
                                "${UrlUtils.getUrl()}/news/${news._id}",
                                newsJson
                            ).execute()
                        }

                        // Update database on success
                        if (newsResponse.isSuccessful && newsResponse.body() != null) {
                            databaseService.executeTransactionAsync { realm ->
                                realm.where(RealmNews::class.java)
                                    .equalTo("id", news.id)
                                    .findFirst()?.let { managedNews ->
                                        managedNews.imageUrls?.clear()
                                        managedNews._id = getString("id", newsResponse.body())
                                        managedNews._rev = getString("rev", newsResponse.body())
                                        managedNews.images = gson.toJson(imagesArray)
                                    }
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

    suspend fun uploadCrashLog() {
        uploadCoordinator.upload(UploadConfigs.CrashLog)
    }

    suspend fun uploadSearchActivity() {
        uploadCoordinator.upload(UploadConfigs.SearchActivity)
    }

    suspend fun uploadResourceActivities(type: String) {
        val config = if (type == "sync") {
            UploadConfigs.ResourceActivitiesSync
        } else {
            UploadConfigs.ResourceActivities
        }
        uploadCoordinator.upload(config)
    }

    suspend fun uploadCourseActivities() {
        uploadCoordinator.upload(UploadConfigs.CourseActivities)
    }

    suspend fun uploadMeetups() {
        uploadCoordinator.upload(UploadConfigs.Meetups)
    }

    suspend fun uploadAdoptedSurveys() {
        uploadCoordinator.upload(UploadConfigs.AdoptedSurveys)
    }
}
