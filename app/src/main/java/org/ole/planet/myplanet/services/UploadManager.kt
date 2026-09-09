package org.ole.planet.myplanet.services

import android.os.SystemClock
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.UploadRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.upload.AchievementUploader
import org.ole.planet.myplanet.services.upload.PhotoUploader
import org.ole.planet.myplanet.services.upload.TeamsUploader
import org.ole.planet.myplanet.services.upload.UploadConfigs
import org.ole.planet.myplanet.services.upload.UploadCoordinator
import org.ole.planet.myplanet.services.upload.UploadResult
import org.ole.planet.myplanet.services.upload.VoicesUploader
import org.ole.planet.myplanet.utils.DispatcherProvider

@Singleton
class UploadManager @Inject constructor(
    private val submissionsRepository: SubmissionsRepository,
    private val uploadCoordinator: UploadCoordinator,
    private val uploadRepository: UploadRepository,
    private val userRepository: UserRepository,
    private val uploadConfigs: UploadConfigs,
    private val resourcesRepository: ResourcesRepository,
    private val teamsUploader: TeamsUploader,
    private val activitiesRepository: ActivitiesRepository,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationScope private val scope: CoroutineScope,
    private val photoUploader: PhotoUploader,
    private val achievementUploader: AchievementUploader,
    private val voicesUploader: VoicesUploader
) : FileUploader(uploadRepository, scope) {

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
        teamsUploader.uploadTeams()
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
        voicesUploader.uploadNews()
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
