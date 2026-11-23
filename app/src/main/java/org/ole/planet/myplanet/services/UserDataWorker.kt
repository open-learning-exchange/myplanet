package org.ole.planet.myplanet.services

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.services.upload.UploadResult

@HiltWorker
class UserDataWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val uploadManager: UploadManager,
    private val uploadToShelfService: UploadToShelfService,
    private val healthRepository: HealthRepository
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
                val successMsg = withTimeoutOrNull(30000.milliseconds) { deferred.await() }
                val outputData = Data.Builder().putString(KEY_SUCCESS_MESSAGE, successMsg).build()
                return@coroutineScope Result.success(outputData)
            } else if (uploadType == UPLOAD_TYPE_BULK) {
                val failedSteps = mutableListOf<String>()
                suspend fun step(name: String, block: suspend () -> Any?) {
                    val outcome = runCatchingRethrowCancellation { block() }
                    val failed = outcome.isFailure ||
                        (outcome.getOrNull() as? UploadResult<*>)?.hasRetryableFailure == true ||
                        outcome.getOrNull() == TIMED_OUT
                    if (failed) {
                        Log.w(TAG, "bulk upload step failed: $name", outcome.exceptionOrNull())
                        failedSteps.add(name)
                    }
                }

                step("achievements") { uploadManager.uploadAchievement() }
                step("news") { uploadManager.uploadNews() }
                step("resource activities") { uploadManager.uploadResourceActivities("") }
                step("course activities") { uploadManager.uploadCourseActivities() }
                step("search activity") { uploadManager.uploadSearchActivity() }
                step("ratings") { uploadManager.uploadRating() }
                step("team tasks") { uploadManager.uploadTeamTask() }
                step("meetups") { uploadManager.uploadMeetups() }
                step("adopted surveys") { uploadManager.uploadAdoptedSurveys() }
                step("submissions") { uploadManager.uploadSubmissions() }
                step("crash logs") { uploadManager.uploadCrashLog() }
                step("user data") {
                    awaitUploadCompletion { onComplete -> uploadToShelfService.uploadUserData { onComplete() } }
                }
                step("health examinations") { healthRepository.syncPendingHealthExaminations() }
                step("user activities") {
                    awaitUploadCompletion { onComplete -> uploadManager.uploadUserActivities { onComplete() } }
                }
                step("exam results") {
                    awaitUploadCompletion { onComplete -> uploadManager.uploadExamResult { onComplete() } }
                }
                step("feedback") { uploadManager.uploadFeedback() }
                step("resources") {
                    awaitUploadCompletion { onComplete -> uploadManager.uploadResource { onComplete() } }
                }
                step("teams") { uploadManager.uploadTeams() }
                step("photos") {
                    awaitUploadCompletion { onComplete -> uploadManager.uploadSubmitPhotos { onComplete() } }
                }
                step("activities") {
                    awaitUploadCompletion { onComplete -> uploadManager.uploadActivities { onComplete() } }
                }

                if (failedSteps.isNotEmpty()) {
                    val output = Data.Builder().putStringArray(KEY_FAILED_STEPS, failedSteps.toTypedArray()).build()
                    return@coroutineScope Result.failure(output)
                }
                return@coroutineScope Result.success()
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading user data", e)
            Result.failure()
        }
    }

    private inline fun <R> runCatchingRethrowCancellation(block: () -> R): kotlin.Result<R> {
        return kotlin.runCatching(block).onFailure { if (it is CancellationException) throw it }
    }

    private suspend fun awaitUploadCompletion(
        timeoutMs: Long = 30000L,
        start: suspend (onComplete: () -> Unit) -> Unit
    ): Any {
        val d = CompletableDeferred<Unit>()
        start { d.complete(Unit) }
        return withTimeoutOrNull(timeoutMs.milliseconds) { d.await() } ?: TIMED_OUT
    }

    companion object {
        const val KEY_UPLOAD_TYPE = "uploadType"
        const val UPLOAD_TYPE_LOGIN = "login"
        const val UPLOAD_TYPE_BULK = "bulk"
        const val KEY_SUCCESS_MESSAGE = "successMessage"
        const val KEY_FAILED_STEPS = "failedSteps"
        private const val TAG = "UserDataWorker"
        private val TIMED_OUT = Any()
    }
}
