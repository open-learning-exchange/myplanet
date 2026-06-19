package org.ole.planet.myplanet.services

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.ole.planet.myplanet.callback.OnSuccessListener

@HiltWorker
class UserDataWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val uploadManager: UploadManager,
    private val uploadToShelfService: UploadToShelfService
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = coroutineScope {
        val uploadType = inputData.getString(KEY_UPLOAD_TYPE) ?: ""

        try {
            if (uploadType == UPLOAD_TYPE_LOGIN) {
                val deferred = CompletableDeferred<String?>()
                uploadManager.uploadUserActivities(object : OnSuccessListener {
                    override fun onSuccess(success: String?) {
                        deferred.complete(success)
                    }
                })
                val successMsg = withTimeoutOrNull(30000L) { deferred.await() }
                val outputData = Data.Builder().putString(KEY_SUCCESS_MESSAGE, successMsg).build()
                return@coroutineScope Result.success(outputData)
            } else if (uploadType == UPLOAD_TYPE_BULK) {
                runCatching { uploadManager.uploadAchievement() }
                runCatching { uploadManager.uploadNews() }
                runCatching { uploadManager.uploadResourceActivities("") }
                runCatching { uploadManager.uploadCourseActivities() }
                runCatching { uploadManager.uploadSearchActivity() }
                runCatching { uploadManager.uploadRating() }
                runCatching { uploadManager.uploadTeamTask() }
                runCatching { uploadManager.uploadMeetups() }
                runCatching { uploadManager.uploadAdoptedSurveys() }
                runCatching { uploadManager.uploadSubmissions() }
                runCatching { uploadManager.uploadCrashLog() }

                runCatching {
                    val d = CompletableDeferred<Unit>()
                    uploadToShelfService.uploadUserData {
                        uploadToShelfService.uploadHealth()
                        d.complete(Unit)
                    }
                    withTimeoutOrNull(30000L) { d.await() }
                }

                runCatching {
                    val d = CompletableDeferred<Unit>()
                    uploadManager.uploadUserActivities(object : OnSuccessListener {
                        override fun onSuccess(success: String?) {
                            d.complete(Unit)
                        }
                    })
                    withTimeoutOrNull(30000L) { d.await() }
                }

                runCatching {
                    val d = CompletableDeferred<Unit>()
                    uploadManager.uploadExamResult(object : OnSuccessListener {
                        override fun onSuccess(success: String?) {
                            d.complete(Unit)
                        }
                    })
                    withTimeoutOrNull(30000L) { d.await() }
                }

                runCatching { uploadManager.uploadFeedback() }

                runCatching {
                    val d = CompletableDeferred<Unit>()
                    uploadManager.uploadResource(object : OnSuccessListener {
                        override fun onSuccess(success: String?) {
                            d.complete(Unit)
                        }
                    })
                    withTimeoutOrNull(30000L) { d.await() }
                    uploadManager.uploadTeams()
                }

                runCatching {
                    val d = CompletableDeferred<Unit>()
                    uploadManager.uploadSubmitPhotos(object : OnSuccessListener {
                        override fun onSuccess(success: String?) {
                            d.complete(Unit)
                        }
                    })
                    withTimeoutOrNull(30000L) { d.await() }
                }

                runCatching {
                    val d = CompletableDeferred<Unit>()
                    uploadManager.uploadActivities(object : OnSuccessListener {
                        override fun onSuccess(success: String?) {
                            d.complete(Unit)
                        }
                    })
                    withTimeoutOrNull(30000L) { d.await() }
                }

                return@coroutineScope Result.success()
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("UserDataWorker", "Error uploading user data", e)
            Result.failure()
        }
    }

    companion object {
        const val KEY_UPLOAD_TYPE = "uploadType"
        const val UPLOAD_TYPE_LOGIN = "login"
        const val UPLOAD_TYPE_BULK = "bulk"
        const val KEY_SUCCESS_MESSAGE = "successMessage"
    }
}
