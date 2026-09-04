package org.ole.planet.myplanet.services

import android.content.Context
import android.os.SystemClock
import android.text.TextUtils
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.NewsUpdateData
import org.ole.planet.myplanet.repository.NewsUploadData
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.UploadRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.services.retry.RetryQueue
import org.ole.planet.myplanet.services.upload.AchievementUploader
import org.ole.planet.myplanet.services.upload.PhotoUploader
import org.ole.planet.myplanet.services.upload.TeamsUploadRunner
import org.ole.planet.myplanet.services.upload.UploadConfigs
import org.ole.planet.myplanet.services.upload.UploadConstants.BATCH_SIZE
import org.ole.planet.myplanet.services.upload.UploadCoordinator
import org.ole.planet.myplanet.services.upload.UploadError
import org.ole.planet.myplanet.services.upload.UploadResult
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.JsonUtils.getString
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.TimeProvider
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.addDocumentOrigin

private inline fun <T> Iterable<T>.processInBatches(action: (List<T>) -> Unit) {
    chunked(BATCH_SIZE).forEach(action)
}

@Singleton
class UploadManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val submissionsRepository: SubmissionsRepository,
    private val gson: Gson,
    private val uploadCoordinator: UploadCoordinator,
    private val uploadRepository: UploadRepository,
    private val retryQueue: RetryQueue,
    private val userRepository: UserRepository,
    private val voicesRepository: VoicesRepository,
    private val uploadConfigs: UploadConfigs,
    private val resourcesRepository: ResourcesRepository,
    private val teamsUploadRunner: TeamsUploadRunner,
    private val activitiesRepository: ActivitiesRepository,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationScope private val scope: CoroutineScope,
    private val photoUploader: PhotoUploader,
    private val achievementUploader: AchievementUploader,
    private val timeProvider: TimeProvider
) : FileUploader(uploadRepository, scope) {

    private suspend fun uploadNewsActivities() {
        uploadCoordinator.uploadRoom(uploadConfigs.NewsActivities)
    }

    private suspend fun notifyListener(listener: OnSuccessListener?, message: String) {
        withContext(dispatcherProvider.mainImmediate) {
            listener?.onSuccess(message)
        }
    }

    fun uploadActivities(listener: OnSuccessListener?) {
        scope.launch {
            val model = userRepository.getUserModel() ?: run {
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

    private fun createImage(user: UserEntity?, imgObject: JsonObject?): JsonObject {
        val `object` = JsonObject()
        `object`.addProperty("title", getString("fileName", imgObject))
        `object`.addProperty("createdDate", timeProvider.now())
        `object`.addProperty("filename", getString("fileName", imgObject))
        `object`.addProperty("private", true)
        user?.id?.let { `object`.addProperty("addedBy", it) }
        user?.parentCode?.let { `object`.addProperty("resideOn", it) }
        user?.planetCode?.let { `object`.addProperty("sourcePlanet", it) }
        val object1 = JsonObject()
        `object`.addDocumentOrigin()
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
        uploadCoordinator.uploadRoom(uploadConfigs.CourseProgress)
    }

    suspend fun uploadFeedback(): Boolean {
        return when (val result = uploadCoordinator.uploadRoom(uploadConfigs.Feedback)) {
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
            val user = userRepository.getUserModel()
            val result = uploadCoordinator.uploadRoom(uploadConfigs.getResourcesConfig(user))

            when (result) {
                is UploadResult.Success -> {
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
                is UploadResult.PartialSuccess -> {
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
                is UploadResult.Failure -> {
                    notifyListener(listener, "Upload failed: ${result.errors.size} errors")
                }
                is UploadResult.Empty -> {
                    notifyListener(listener, "No resources to upload")
                }
            }
        } catch (e: Exception) {
            Log.e("UploadManager", "Resource upload failed", e)
            notifyListener(listener, "Resource upload failed: ${e.message}")
        }
    }

    suspend fun uploadTeamTask() {
        uploadCoordinator.uploadRoom(uploadConfigs.TeamTask)
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
        teamsUploadRunner.uploadTeams()
    }

    suspend fun uploadUserActivities(listener: OnSuccessListener) {
        val model = userRepository.getUserModel() ?: run {
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
        uploadCoordinator.uploadRoom(uploadConfigs.TeamActivities)
    }

    suspend fun uploadRating() {
        uploadCoordinator.uploadRoom(uploadConfigs.Rating)
    }

    suspend fun uploadNews() {
        // Note: uploadNews has unique logic that requires uploading images BEFORE the news document,
        // then modifying the serialized JSON based on image upload responses. This doesn't fit the
        // standard UploadCoordinator pattern (a single serialize-then-POST/PUT per item), so the
        // orchestration stays custom here — but the actual doc-level network calls and retry-queueing
        // now reuse the same UploadRepository/RetryQueue primitives UploadCoordinator uses, instead of
        // reimplementing them.
        val user = userRepository.getUserModel()
        val newsItems = voicesRepository.getNewsForUpload()

        withContext(dispatcherProvider.io) {
            newsItems.processInBatches { batch ->
                val successfulUpdates = mutableListOf<NewsUpdateData>()
                val bulkDocsArray = JsonArray()
                val processedNews = mutableListOf<Pair<NewsUploadData, JsonArray>>()

                batch.forEach { news ->
                    try {
                        // Upload images first and collect metadata
                        val imagesArray = JsonArray()
                        val messageWithImages = StringBuilder(news.message ?: "")

                        news.imageUrls.forEach { imageUrl ->
                            val imgObject = gson.fromJson(imageUrl, JsonObject::class.java)

                            // Create image resource document
                            val imageDoc = createImage(user, imgObject)
                            val imageResponse = uploadRepository.postUpload(
                                "${UrlUtils.getUrl()}/resources",
                                imageDoc
                            ).body()

                            val resourceId = getString("id", imageResponse)
                            val resourceRev = getString("rev", imageResponse)

                            // Upload image file as attachment
                            val imageFile = File(getString("imageUrl", imgObject))
                            val fileName = FileUtils.getFileNameFromUrl(getString("imageUrl", imgObject))
                            val mimeType = imageFile.toURI().toURL().openConnection().contentType
                            val fileBody = imageFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())

                            uploadRepository.uploadResource(
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

                            messageWithImages.append("\n").append(markdown)
                        }

                        val newsJson = news.newsJson
                        newsJson.addProperty("message", messageWithImages.toString())
                        newsJson.add("images", imagesArray)

                        bulkDocsArray.add(newsJson)
                        processedNews.add(Pair(news, imagesArray))
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception in UploadManager processing images for news", e)
                        val isCreate = TextUtils.isEmpty(news._id)
                        queueNewsRetry(news, news.newsJson, null, if (isCreate) "POST" else "PUT", e)
                    }
                }

                if (!bulkDocsArray.isEmpty()) {
                    val bulkRequest = JsonObject()
                    bulkRequest.add("docs", bulkDocsArray)

                    try {
                        val response = uploadRepository.postUploadArray("${UrlUtils.getUrl()}/news/_bulk_docs", bulkRequest)
                        val responseBody = response.body()

                        if (response.isSuccessful && responseBody != null) {
                            for (i in 0 until responseBody.size()) {
                                val itemResponse = responseBody.get(i).asJsonObject
                                val (news, imagesArray) = processedNews[i]

                                if (itemResponse.has("error")) {
                                    val isCreate = TextUtils.isEmpty(news._id)
                                    val errorReason = itemResponse.get("error").asString
                                    queueNewsRetry(news, news.newsJson, null, if (isCreate) "POST" else "PUT", Exception("Bulk upload error: $errorReason"))
                                } else {
                                    successfulUpdates.add(NewsUpdateData(
                                        id = news.id,
                                        _id = getString("id", itemResponse),
                                        _rev = getString("rev", itemResponse),
                                        imagesArray = imagesArray
                                    ))
                                }
                            }
                        } else {
                            processedNews.forEach { (news, _) ->
                                val isCreate = TextUtils.isEmpty(news._id)
                                queueNewsRetry(news, news.newsJson, response.code(), if (isCreate) "POST" else "PUT")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception in UploadManager bulk upload", e)
                        processedNews.forEach { (news, _) ->
                            val isCreate = TextUtils.isEmpty(news._id)
                            queueNewsRetry(news, news.newsJson, null, if (isCreate) "POST" else "PUT", e)
                        }
                    }
                }

                if (successfulUpdates.isNotEmpty()) {
                    voicesRepository.markNewsUploaded(successfulUpdates)
                }
            }
        }
        uploadNewsActivities()
    }

    private suspend fun queueNewsRetry(
        news: NewsUploadData,
        payload: JsonObject,
        httpCode: Int?,
        httpMethod: String,
        exception: Exception? = null
    ) {
        val retryable = exception != null || (httpCode != null && httpCode >= 500)
        if (!retryable) return
        retryQueue.queueFailedOperation(
            uploadType = "News",
            error = UploadError(
                itemId = news.id ?: "",
                exception = exception ?: Exception("Upload failed: HTTP $httpCode"),
                retryable = true,
                httpCode = httpCode
            ),
            payload = payload,
            endpoint = "news",
            httpMethod = httpMethod,
            dbId = news._id,
            modelClassName = "News"
        )
    }

    suspend fun uploadCrashLog() {
        uploadCoordinator.uploadRoom(uploadConfigs.CrashLog)
    }

    suspend fun uploadSearchActivity() {
        uploadCoordinator.uploadRoom(uploadConfigs.SearchActivity)
    }

    suspend fun uploadResourceActivities(type: String) {
        val config = if (type == "sync") {
            uploadConfigs.ResourceActivitiesSync
        } else {
            uploadConfigs.ResourceActivities
        }
        uploadCoordinator.uploadRoom(config)
    }

    suspend fun uploadCourseActivities() {
        uploadCoordinator.uploadRoom(uploadConfigs.CourseActivities)
    }

    suspend fun uploadMeetups() {
        uploadCoordinator.uploadRoom(uploadConfigs.Meetups)
    }

    suspend fun uploadAdoptedSurveys() {
        uploadCoordinator.upload(uploadConfigs.AdoptedSurveys)
    }

    companion object {
        private const val TAG = "UploadManager"
    }
}
