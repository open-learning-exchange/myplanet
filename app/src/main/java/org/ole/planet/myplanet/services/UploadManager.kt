package org.ole.planet.myplanet.services

import android.content.Context
import android.os.SystemClock
import android.text.TextUtils
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.model.RealmMyPersonal
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.ChatRepository
import org.ole.planet.myplanet.repository.PersonalsRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.upload.PhotoUploader
import org.ole.planet.myplanet.services.upload.UploadConfigs
import org.ole.planet.myplanet.services.upload.UploadConstants.BATCH_SIZE
import org.ole.planet.myplanet.services.upload.UploadCoordinator
import org.ole.planet.myplanet.services.upload.UploadResult
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.JsonUtils.getString
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.TimeProvider
import org.ole.planet.myplanet.utils.UrlUtils

private inline fun <T> Iterable<T>.processInBatches(action: (List<T>) -> Unit) {
    chunked(BATCH_SIZE).forEach(action)
}

@Singleton
class UploadManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val submissionsRepository: SubmissionsRepository,
    private val sharedPrefManager: SharedPrefManager,
    private val gson: Gson,
    private val uploadCoordinator: UploadCoordinator,
    private val personalsRepository: PersonalsRepository,
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    private val voicesRepository: org.ole.planet.myplanet.repository.VoicesRepository,
    private val uploadConfigs: UploadConfigs,
    private val resourcesRepository: org.ole.planet.myplanet.repository.ResourcesRepository,
    private val teamsRepository: Lazy<TeamsRepository>,
    private val teamsSyncRepository: Lazy<org.ole.planet.myplanet.repository.TeamsSyncRepository>,
    private val apiInterface: ApiInterface,
    private val activitiesRepository: org.ole.planet.myplanet.repository.ActivitiesRepository,
    private val dispatcherProvider: org.ole.planet.myplanet.utils.DispatcherProvider,
    @ApplicationScope private val scope: CoroutineScope,
    private val photoUploader: PhotoUploader,
    private val achievementUploader: org.ole.planet.myplanet.services.upload.AchievementUploader,
    private val timeProvider: TimeProvider
) : FileUploader(apiInterface, scope) {

    private suspend fun uploadNewsActivities() {
        uploadCoordinator.upload(uploadConfigs.NewsActivities)
    }

    private suspend fun notifyListener(listener: OnSuccessListener?, message: String) {
        withContext(dispatcherProvider.main) {
            listener?.onSuccess(message)
        }
    }

    fun uploadActivities(listener: OnSuccessListener?) {
        scope.launch {
            val model = userRepository.getUserModelSuspending() ?: run {
                notifyListener(listener, "Cannot upload activities: user model is null")
                return@launch
            }

            if (model.isManager()) {
                notifyListener(listener, "Skipping activities upload for manager")
                return@launch
            }

            try {
                activitiesRepository.uploadMyPlanetActivities(model)
                notifyListener(listener, "My planet activities uploaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Exception in UploadManager", e)
                notifyListener(listener, "Failed to upload activities: ${e.message}")
            }
        }
    }

    suspend fun uploadExamResult(listener: OnSuccessListener) {
        withContext(dispatcherProvider.io) {
            try {
                val result = uploadCoordinator.upload(uploadConfigs.ExamResults)

                val message = when (result) {
                    is UploadResult.Success -> "Result sync completed successfully (${result.data} processed, 0 errors)"
                    is UploadResult.PartialSuccess -> "Result sync completed with issues (${result.succeeded.size} processed, ${result.failed.size} errors)"
                    is UploadResult.Failure -> "Result sync failed: ${result.errors.size} errors"
                    is UploadResult.Empty -> "No exam results to upload"
                }

                uploadCourseProgress()
                notifyListener(listener, message)
            } catch (e: Exception) {
                Log.e(TAG, "Exception in UploadManager", e)
                notifyListener(listener, "Error during result sync: ${e.message}")
            }
        }
    }

    private fun createImage(user: RealmUser?, imgObject: JsonObject?): JsonObject {
        val `object` = JsonObject()
        `object`.addProperty("title", getString("fileName", imgObject))
        `object`.addProperty("createdDate", timeProvider.now())
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
        achievementUploader.uploadAchievement()
    }

    private suspend fun uploadCourseProgress() {
        uploadCoordinator.upload(uploadConfigs.CourseProgress)
    }

    suspend fun uploadFeedback(): Boolean {
        return when (val result = uploadCoordinator.upload(uploadConfigs.Feedback)) {
            is UploadResult.Success -> true
            is UploadResult.PartialSuccess -> result.failed.isEmpty()
            is UploadResult.Failure -> false
            is UploadResult.Empty -> true
        }
    }

    suspend fun uploadSubmitPhotos(listener: OnSuccessListener?) {
        val resultMessage = photoUploader.uploadSubmitPhotos(listener)
        resultMessage?.let {
            notifyListener(listener, it)
        }
    }
    suspend fun uploadResource(listener: OnSuccessListener?) {
        try {
            val user = userRepository.getUserModelSuspending()
            val result = uploadCoordinator.upload(uploadConfigs.getResourcesConfig(user))

            when (result) {
                is org.ole.planet.myplanet.services.upload.UploadResult.Success -> {
                    listener?.let { l ->
                        val libraryIds = result.items.map { it.localId }
                        if (libraryIds.isNotEmpty()) {
                            val libraries = resourcesRepository.getLibraryItemsByIds(libraryIds)
                            val libMap = libraries.associateBy { it.id }

                            result.items.forEach { item ->
                                libMap[item.localId]?.let { library ->
                                    uploadAttachment(item.remoteId, item.remoteRev, library, l)
                                }
                            }
                        }
                    }
                    notifyListener(listener, "Uploaded ${result.items.size} resources successfully")
                }
                is org.ole.planet.myplanet.services.upload.UploadResult.PartialSuccess -> {
                    listener?.let { l ->
                        val libraryIds = result.succeeded.map { it.localId }
                        if (libraryIds.isNotEmpty()) {
                            val libraries = resourcesRepository.getLibraryItemsByIds(libraryIds)
                            val libMap = libraries.associateBy { it.id }

                            result.succeeded.forEach { item ->
                                libMap[item.localId]?.let { library ->
                                    uploadAttachment(item.remoteId, item.remoteRev, library, l)
                                }
                            }
                        }
                    }
                    notifyListener(listener, "Partial success: ${result.succeeded.size} succeeded, ${result.failed.size} failed")
                }
                is org.ole.planet.myplanet.services.upload.UploadResult.Failure -> {
                    notifyListener(listener, "Upload failed: ${result.errors.size} errors")
                }
                is org.ole.planet.myplanet.services.upload.UploadResult.Empty -> {
                    notifyListener(listener, "No resources to upload")
                }
            }
        } catch (e: Exception) {
            Log.e("UploadManager", "Resource upload failed", e)
            notifyListener(listener, "Resource upload failed: ${e.message}")
        }
    }

    suspend fun uploadMyPersonal(personal: RealmMyPersonal): String {
        if (!personal.isUploaded) {
            return withContext(dispatcherProvider.io) {
                try {
                    val result = personalsRepository.uploadPersonalDocument(personal)
                    if (result != null) {
                        val (id, rev) = result
                        uploadAttachment(id, rev, personal) { }
                        "Personal resource uploaded successfully"
                    } else {
                        "Failed to upload personal resource: No response"
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception in UploadManager", e)
                    "Unable to upload resource: ${e.message}"
                }
            }
        } else {
            return "Resource already uploaded"
        }
    }

    suspend fun uploadTeamTask() {
        uploadCoordinator.upload(uploadConfigs.TeamTask)
    }

    suspend fun uploadSubmissions(buttonClickTime: Long = 0L) {
        Log.d("UploadManager", "uploadSubmissions called with buttonClickTime: $buttonClickTime")
        val startTime = if (buttonClickTime > 0) buttonClickTime else SystemClock.elapsedRealtime()

        if (buttonClickTime > 0) {
            Log.d("UploadManager", "Mini survey sync timer started from button click at: $startTime")
        } else {
            Log.d("UploadManager", "Mini survey sync started at: $startTime (buttonClickTime was $buttonClickTime)")
        }

        try {
            val result = uploadCoordinator.upload(uploadConfigs.Submissions)

            Log.d("UploadManager", when (result) {
                is UploadResult.Success -> "Uploaded ${result.data} submissions successfully"
                is UploadResult.PartialSuccess -> "Partial success: ${result.succeeded.size} succeeded, ${result.failed.size} failed"
                is UploadResult.Failure -> "Upload failed: ${result.errors.size} errors"
                is UploadResult.Empty -> "No submissions to upload"
            })
        } catch (e: Exception) {
            Log.e("UploadManager", "Error uploading submissions", e)
        } finally {
            val endTime = SystemClock.elapsedRealtime()
            val duration = endTime - startTime
            Log.d("UploadManager", "Mini survey sync completed at: $endTime")
            Log.d("UploadManager", "Total time from button click to sync completion: ${duration}ms (${duration / 1000.0}s)")
        }
    }

    suspend fun uploadTeams() {
        val teamsToUpload = teamsSyncRepository.get().getTeamsForUpload()

        withContext(dispatcherProvider.io) {
            teamsToUpload.processInBatches { batch ->
                batch.forEach { teamData ->
                    try {
                        if (teamData.isDeletePending) {
                            val id = teamData.teamId ?: return@forEach
                            val response = apiInterface.putDoc(
                                UrlUtils.header, "application/json",
                                "${UrlUtils.getUrl()}/teams/$id", teamData.serialized
                            )
                            if (response.isSuccessful) {
                                teamsSyncRepository.get().deleteLocalTeamRecord(id)
                            }
                        } else {
                            val response = apiInterface.postDoc(
                                UrlUtils.header, "application/json",
                                "${UrlUtils.getUrl()}/teams", teamData.serialized
                            )

                            val `object` = response.body()

                            if (`object` != null) {
                                val rev = getString("rev", `object`)
                                teamsSyncRepository.get().markTeamUploaded(teamData.teamId, rev)
                                if (!teamData.imageName.isNullOrEmpty() && teamData.teamId != null && rev.isNotEmpty()) {
                                    uploadTeamImageAttachment(teamData.teamId, rev, teamData.imageName)
                                }
                            }
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Exception in UploadManager", e)
                    }
                }
            }
        }
    }

    private suspend fun uploadTeamImageAttachment(teamId: String, rev: String, imageName: String) {
        val imageFile = org.ole.planet.myplanet.model.RealmMyTeam
            .getAttachmentFile(context, teamId, imageName) ?: return
        if (!imageFile.exists()) return
        try {
            val mimeType = FileUtils.getMimeType(imageName) ?: "image/*"
            val body = imageFile.readBytes().toRequestBody(mimeType.toMediaTypeOrNull())
            val encodedName = android.net.Uri.encode(imageName)
            val url = "${UrlUtils.getUrl()}/teams/$teamId/$encodedName"
            val response = apiInterface.uploadResource(FileUploader.getHeaderMap(mimeType, rev), url, body)
            val newRev = response.body()?.get("rev")?.asString
            if (!newRev.isNullOrEmpty()) {
                teamsSyncRepository.get().markTeamUploaded(teamId, newRev)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload team image attachment", e)
        }
    }

    suspend fun uploadUserActivities(listener: OnSuccessListener) {
        val model = userRepository.getUserModelSuspending() ?: run {
            notifyListener(listener, "Cannot upload user activities: user model is null")
            return
        }

        if (model.isManager()) {
            notifyListener(listener, "Skipping user activities upload for manager")
            return
        }

        try {
            activitiesRepository.uploadActivities()

            uploadTeamActivities()

            notifyListener(listener, "User activities sync completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Exception in UploadManager", e)
            notifyListener(listener, "Failed to upload user activities: ${e.message}")
        }
    }

    suspend fun uploadTeamActivities() {
        uploadCoordinator.upload(uploadConfigs.TeamActivities)
    }

    suspend fun uploadRating() {
        uploadCoordinator.upload(uploadConfigs.Rating)
    }

    suspend fun uploadNews() {
        // Note: uploadNews has unique logic that requires uploading images BEFORE the news document,
        // then modifying the serialized JSON based on image upload responses. This doesn't fit the
        // standard UploadCoordinator pattern, so we handle it with custom logic but still use
        // the coordinator for the core upload/update flow where possible.
        val user = userRepository.getUserModelSuspending()
        val newsItems = voicesRepository.getNewsForUpload()

        withContext(dispatcherProvider.io) {
            newsItems.processInBatches { batch ->
                val successfulUpdates = mutableListOf<org.ole.planet.myplanet.repository.NewsUpdateData>()
                batch.forEach { news ->
                    try {
                        // Upload images first and collect metadata
                        val imagesArray = com.google.gson.JsonArray()
                        var messageWithImages = news.message ?: ""

                        news.imageUrls.forEach { imageUrl ->
                            val imgObject = gson.fromJson(imageUrl, JsonObject::class.java)

                            // Create image resource document
                            val imageDoc = createImage(user, imgObject)
                            val imageResponse = apiInterface.postDoc(
                                UrlUtils.header,
                                "application/json",
                                "${UrlUtils.getUrl()}/resources",
                                imageDoc
                            ).body()

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
                            )

                            val resourceObject = JsonObject()
                            resourceObject.addProperty("resourceId", resourceId)
                            resourceObject.addProperty("filename", fileName)
                            val markdown = "![](resources/$resourceId/$fileName)"
                            resourceObject.addProperty("markdown", markdown)
                            imagesArray.add(resourceObject)

                            messageWithImages += "\n$markdown"
                        }

                        val newsJson = news.newsJson
                        newsJson.addProperty("message", messageWithImages)
                        newsJson.add("images", imagesArray)

                        // Upload news document (POST or PUT)
                        val newsResponse = if (TextUtils.isEmpty(news._id)) {
                            apiInterface.postDoc(
                                UrlUtils.header,
                                "application/json",
                                "${UrlUtils.getUrl()}/news",
                                newsJson
                            )
                        } else {
                            apiInterface.putDoc(
                                UrlUtils.header,
                                "application/json",
                                "${UrlUtils.getUrl()}/news/${news._id}",
                                newsJson
                            )
                        }

                        // Update database on success
                        if (newsResponse.isSuccessful && newsResponse.body() != null) {
                            val body = newsResponse.body()
                            successfulUpdates.add(org.ole.planet.myplanet.repository.NewsUpdateData(
                                id = news.id,
                                _id = getString("id", body),
                                _rev = getString("rev", body),
                                imagesArray = imagesArray
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception in UploadManager", e)
                    }
                }

                if (successfulUpdates.isNotEmpty()) {
                    voicesRepository.markNewsUploaded(successfulUpdates)
                }
            }
        }
        uploadNewsActivities()
    }

    suspend fun uploadCrashLog() {
        uploadCoordinator.upload(uploadConfigs.CrashLog)
    }

    suspend fun uploadSearchActivity() {
        uploadCoordinator.upload(uploadConfigs.SearchActivity)
    }

    suspend fun uploadResourceActivities(type: String) {
        val config = if (type == "sync") {
            uploadConfigs.ResourceActivitiesSync
        } else {
            uploadConfigs.ResourceActivities
        }
        uploadCoordinator.upload(config)
    }

    suspend fun uploadCourseActivities() {
        uploadCoordinator.upload(uploadConfigs.CourseActivities)
    }

    suspend fun uploadMeetups() {
        uploadCoordinator.upload(uploadConfigs.Meetups)
    }

    suspend fun uploadAdoptedSurveys() {
        uploadCoordinator.upload(uploadConfigs.AdoptedSurveys)
    }

    companion object {
        private const val TAG = "UploadManager"
    }
}
