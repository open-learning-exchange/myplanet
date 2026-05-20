package org.ole.planet.myplanet.services

import android.content.Context
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
import org.ole.planet.myplanet.data.DatabaseService
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
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.VersionUtils.getAndroidId

private inline fun <T> Iterable<T>.processInBatches(action: (T) -> Unit) {
    chunked(BATCH_SIZE).forEach { chunk ->
        chunk.forEach { item ->
            action(item)
        }
    }

}

@Singleton
class UploadManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val databaseService: DatabaseService,
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
    private val apiInterface: ApiInterface,
    private val activitiesRepository: org.ole.planet.myplanet.repository.ActivitiesRepository,
    private val dispatcherProvider: org.ole.planet.myplanet.utils.DispatcherProvider,
    @ApplicationScope private val scope: CoroutineScope,
    private val photoUploader: PhotoUploader
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
        `object`.addProperty("createdDate", System.currentTimeMillis())
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
        val list = userRepository.getAchievementsForUpload()
        if (list.isEmpty()) return
        withContext(dispatcherProvider.io) {
            list.forEach { achievement ->
                val id = achievement.get("_id")?.asString ?: return@forEach
                val url = "${UrlUtils.getUrl()}/achievements/$id"
                try {
                    val response = apiInterface.putDoc(UrlUtils.header, "application/json", url, achievement)
                    if (response.isSuccessful) {
                        val rev = response.body()?.get("rev")?.asString
                        userRepository.markAchievementUploaded(id, rev)
                        val resumeFileName = achievement.get("resumeFileName")?.asString ?: ""
                        if (resumeFileName.isNotEmpty() && !rev.isNullOrEmpty()) {
                            uploadCvAttachment(id, rev, resumeFileName)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception in UploadManager", e)
                }
            }
        }
    }

    private suspend fun uploadCvAttachment(docId: String, rev: String, resumeFileName: String) {
        val cvFile = File(FileUtils.getOlePath(context) + "cv/$resumeFileName")
        if (!cvFile.exists()) return
        try {
            val body = cvFile.readBytes().toRequestBody("application/pdf".toMediaTypeOrNull())
            // CouchDB attachment key is always "resume.pdf"
            val url = "${UrlUtils.getUrl()}/achievements/$docId/resume.pdf"
            apiInterface.uploadResource(FileUploader.getHeaderMap("application/pdf", rev), url, body)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload CV attachment", e)
        }
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

            val resourcesToUpload = resourcesRepository.getUnuploadedResources(user)

            if (resourcesToUpload.isEmpty()) {
                notifyListener(listener, "No resources to upload")
                return
            }

            withContext(dispatcherProvider.io) {
                resourcesToUpload.chunked(BATCH_SIZE).forEach { batch ->
                    val successfulUpdates = mutableListOf<Pair<org.ole.planet.myplanet.repository.ResourceUploadData, com.google.gson.JsonObject>>()

                    batch.forEach { resourceData ->
                        try {
                            val `object` = apiInterface.postDoc(
                                UrlUtils.header, "application/json",
                                "${UrlUtils.getUrl()}/resources", resourceData.serialized
                            ).body()

                            if (`object` != null) {
                                successfulUpdates.add(Pair(resourceData, `object`))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Exception in UploadManager", e)
                        }
                    }

                    if (successfulUpdates.isNotEmpty()) {
                        val libraryIds = successfulUpdates.mapNotNull { it.first.libraryId }.toTypedArray()
                        var isTransactionSuccessful = false

                        try {
                            val uploadedInfos = successfulUpdates.mapNotNull { (resourceData, `object`) ->
                                val rev = getString("rev", `object`)
                                val id = getString("id", `object`)
                                resourceData.libraryId?.let { libId ->
                                    org.ole.planet.myplanet.repository.UploadedResourceInfo(
                                        libraryId = libId,
                                        id = id,
                                        rev = rev,
                                        isPrivate = resourceData.isPrivate,
                                        privateFor = resourceData.privateFor,
                                        title = resourceData.title
                                    )
                                }
                            }

                            val planetCode = user?.planetCode?.takeIf { it.isNotBlank() }
                                ?: sharedPrefManager.getPlanetCode()

                            resourcesRepository.markResourcesUploaded(uploadedInfos, planetCode)
                            isTransactionSuccessful = true
                        } catch (e: Exception) {
                            // If the executeTransaction block throws (e.g. disk full, schema conflict),
                            // Realm automatically rolls back the entire transaction.
                            // We catch it here to prevent crashing the batch loop and prevent
                            // `isTransactionSuccessful` from being set to true, so we don't upload
                            // attachments for failed DB writes.
                            Log.e(TAG, "Exception in UploadManager", e)
                        }

                        if (isTransactionSuccessful) {
                            listener?.let {
                                try {
                                    val libraries = resourcesRepository.getLibraryItemsByIds(libraryIds.toList())

                                    val libMap = libraries.associateBy { it.id }

                                    successfulUpdates.forEach { (resourceData, `object`) ->
                                        val rev = getString("rev", `object`)
                                        val id = getString("id", `object`)

                                        resourceData.libraryId?.let { libId ->
                                            libMap[libId]?.let { library ->
                                                uploadAttachment(id, rev, library, listener)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("UploadManager", "Error uploading attachments", e)
                                }
                            }
                        }
                    }
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
                    val response = apiInterface.postDoc(
                        UrlUtils.header, "application/json",
                        "${UrlUtils.getUrl()}/resources", RealmMyPersonal.serialize(personal, context)
                    )

                    val `object` = response.body()
                    if (`object` != null) {
                        val rev = getString("rev", `object`)
                        val id = getString("id", `object`)

                        personal.id?.let { personalId ->
                            personalsRepository.updatePersonalAfterSync(personalId, id, rev)
                        }

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
        val startTime = if (buttonClickTime > 0) buttonClickTime else System.currentTimeMillis()

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
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            Log.d("UploadManager", "Mini survey sync completed at: $endTime")
            Log.d("UploadManager", "Total time from button click to sync completion: ${duration}ms (${duration / 1000.0}s)")
        }
    }

    suspend fun uploadTeams() {
        val teamsToUpload = teamsRepository.get().getTeamsForUpload()

        withContext(dispatcherProvider.io) {
            teamsToUpload.chunked(BATCH_SIZE).forEach { batch ->
                batch.forEach { teamData ->
                    try {
                        if (teamData.isDeletePending) {
                            val id = teamData.teamId ?: return@forEach
                            val response = apiInterface.putDoc(
                                UrlUtils.header, "application/json",
                                "${UrlUtils.getUrl()}/teams/$id", teamData.serialized
                            )
                            if (response.isSuccessful) {
                                teamsRepository.get().deleteLocalTeamRecord(id)
                            }
                        } else {
                            val response = apiInterface.postDoc(
                                UrlUtils.header, "application/json",
                                "${UrlUtils.getUrl()}/teams", teamData.serialized
                            )

                            val `object` = response.body()

                            if (`object` != null) {
                                val rev = getString("rev", `object`)
                                teamsRepository.get().markTeamUploaded(teamData.teamId, rev)
                            }
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Exception in UploadManager", e)
                    }
                }
            }
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
            newsItems.chunked(BATCH_SIZE).forEach { batch ->
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
