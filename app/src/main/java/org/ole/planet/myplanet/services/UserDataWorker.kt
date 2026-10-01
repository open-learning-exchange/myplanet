package org.ole.planet.myplanet.services

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
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
                runCatchingRethrowCancellation { uploadManager.uploadAchievement() }
                runCatchingRethrowCancellation { uploadManager.uploadNews() }
                runCatchingRethrowCancellation { uploadManager.uploadResourceActivities("") }
                runCatchingRethrowCancellation { uploadManager.uploadCourseActivities() }
                runCatchingRethrowCancellation { uploadManager.uploadSearchActivity() }
                runCatchingRethrowCancellation { uploadManager.uploadRating() }
                runCatchingRethrowCancellation { uploadManager.uploadTeamTask() }
                runCatchingRethrowCancellation { uploadManager.uploadMeetups() }
                runCatchingRethrowCancellation { uploadManager.uploadAdoptedSurveys() }
                runCatchingRethrowCancellation { uploadManager.uploadSubmissions() }
                runCatchingRethrowCancellation { uploadManager.uploadCrashLog() }

                runCatchingRethrowCancellation {
                    awaitUploadCompletion { onComplete ->
                        uploadToShelfService.uploadUserData {
                            uploadToShelfService.uploadHealth()
                            onComplete()
                        }
                    }
                }

                runCatchingRethrowCancellation {
                    awaitUploadCompletion { onComplete ->
                        uploadManager.uploadUserActivities { onComplete() }
                    }
                }

                runCatchingRethrowCancellation {
                    awaitUploadCompletion { onComplete ->
                        uploadManager.uploadExamResult { onComplete() }
                    }
                }

                runCatchingRethrowCancellation { uploadManager.uploadFeedback() }

                runCatchingRethrowCancellation {
                    awaitUploadCompletion { onComplete ->
                        uploadManager.uploadResource { onComplete() }
                    }
                    uploadManager.uploadTeams()
                }

                runCatchingRethrowCancellation {
                    awaitUploadCompletion { onComplete ->
                        uploadManager.uploadSubmitPhotos { onComplete() }
                    }
                }

                runCatchingRethrowCancellation {
                    awaitUploadCompletion { onComplete ->
                        uploadManager.uploadActivities { onComplete() }
                    }
                }

                return@coroutineScope Result.success()
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("UserDataWorker", "Error uploading user data", e)
            Result.failure()
        }
    }

    private inline fun <R> runCatchingRethrowCancellation(block: () -> R): kotlin.Result<R> {
        return kotlin.runCatching(block).onFailure { if (it is CancellationException) throw it }
    }

    private suspend fun awaitUploadCompletion(
        timeoutMs: Long = 30000L,
        start: suspend (onComplete: () -> Unit) -> Unit
    ) {
        val d = CompletableDeferred<Unit>()
        start { d.complete(Unit) }
        withTimeoutOrNull(timeoutMs) { d.await() }
    }

    companion object {
        const val KEY_UPLOAD_TYPE = "uploadType"
        const val UPLOAD_TYPE_LOGIN = "login"
        const val UPLOAD_TYPE_BULK = "bulk"
        const val KEY_SUCCESS_MESSAGE = "successMessage"
    }
}
