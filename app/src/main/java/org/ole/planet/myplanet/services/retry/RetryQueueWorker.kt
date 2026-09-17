package org.ole.planet.myplanet.services.retry

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.google.gson.JsonParser
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.model.RetryOperation
import org.ole.planet.myplanet.repository.RetryOperationResult
import org.ole.planet.myplanet.repository.RetryRepository
import org.ole.planet.myplanet.utils.UrlUtils

@HiltWorker
class RetryQueueWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val retryQueue: RetryQueue,
    private val retryRepository: RetryRepository
) : CoroutineWorker(context, workerParams) {

    @Deprecated("For backward compatibility and existing unit tests")
    constructor(
        context: Context,
        workerParams: WorkerParameters,
        retryQueue: RetryQueue,
        apiInterface: ApiInterface
    ) : this(
        context,
        workerParams,
        retryQueue,
        object : RetryRepository {
            override suspend fun executeOperation(operation: RetryOperation): RetryOperationResult {
                retryQueue.markInProgress(operation.id)
                val payload = try {
                    JsonParser.parseString(operation.serializedPayload).asJsonObject
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Invalid payload for ${operation.id}, abandoning")
                    retryQueue.markFailed(operation.id, "Invalid payload", null)
                    return RetryOperationResult.TerminalFailure("Invalid payload", null)
                }
                val baseUrl = UrlUtils.getUrl()
                val authHeader = UrlUtils.header
                val requestUrl = if (operation.dbId.isNullOrEmpty()) {
                    "$baseUrl/${operation.endpoint}"
                } else {
                    "$baseUrl/${operation.endpoint}/${operation.dbId}"
                }

                return try {
                    val response = if (operation.httpMethod == "PUT" && !operation.dbId.isNullOrEmpty()) {
                        apiInterface.putDoc(authHeader, "application/json", requestUrl, payload)
                    } else {
                        apiInterface.postDoc(authHeader, "application/json", requestUrl, payload)
                    }

                    if (response.isSuccessful || response.code() == 409) {
                        retryQueue.markCompleted(operation.id)
                        RetryOperationResult.Success
                    } else {
                        val code = response.code()
                        val isRetryable = code >= 500
                        val msg = if (isRetryable) "HTTP $code" else "Non-retryable HTTP $code"
                        retryQueue.markFailed(operation.id, msg, code)
                        if (isRetryable) RetryOperationResult.RetryableFailure(msg, code)
                        else RetryOperationResult.TerminalFailure(msg, code)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    retryQueue.markFailed(operation.id, e.message, null)
                    RetryOperationResult.RetryableFailure(e.message, null)
                } catch (e: Exception) {
                    retryQueue.markFailed(operation.id, e.message, null)
                    RetryOperationResult.RetryableFailure(e.message, null)
                }
            }

            override suspend fun enqueue(uploadType: String, failure: org.ole.planet.myplanet.model.RetryFailure, payload: String, endpoint: String, httpMethod: String, dbId: String?, modelClassName: String, userId: String?) {}
            override suspend fun updateAttempt(operationId: String, failure: org.ole.planet.myplanet.model.RetryFailure) {}
            override suspend fun markInProgress(operationId: String) { retryQueue.markInProgress(operationId) }
            override suspend fun markCompleted(operationId: String) { retryQueue.markCompleted(operationId) }
            override suspend fun markFailed(operationId: String, errorMessage: String?, httpCode: Int?) { retryQueue.markFailed(operationId, errorMessage, httpCode) }
            override suspend fun getPending(): List<RetryOperation> = retryQueue.getPendingOperations()
            override suspend fun getPendingCount(): Long = 0
            override suspend fun cleanup() { retryQueue.cleanup() }
            override suspend fun getExistingOperation(itemId: String, uploadType: String): RetryOperation? = null
            override suspend fun deletePendingAndAbandonedOperations() {}
            override suspend fun recoverStuckOperations() { retryQueue.recoverStuckOperations() }
            override fun isCurrentlyProcessing(): Boolean = retryQueue.isCurrentlyProcessing()
            override fun setProcessing(processing: Boolean) {}
            override suspend fun safeClearQueue(): Boolean = retryQueue.safeClearQueue()
            override suspend fun getRetryQueueSnapshot(): org.ole.planet.myplanet.repository.RetryQueueDetails = org.ole.planet.myplanet.repository.RetryQueueDetails()
        }
    )

    companion object {
        private const val TAG = "RetryQueueWorker"
        private const val WORK_NAME = "retryQueueWork"
        private const val BATCH_SIZE = 50
        private const val MAX_CONCURRENT_RETRIES = 6

        fun schedule(context: Context) {
            val workRequest = createScheduleWorkRequest()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
            Log.d(TAG, "Scheduled RetryQueueWorker")
        }

        @VisibleForTesting
        internal fun createScheduleWorkRequest(): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            return PeriodicWorkRequestBuilder<RetryQueueWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
        }

        fun triggerImmediateRetry(context: Context) {
            val workRequest = createImmediateRetryWorkRequest()

            WorkManager.getInstance(context).enqueue(workRequest)
            Log.d(TAG, "Triggered immediate retry")
        }

        @VisibleForTesting
        internal fun createImmediateRetryWorkRequest(): OneTimeWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            return OneTimeWorkRequestBuilder<RetryQueueWorker>()
                .setConstraints(constraints)
                .build()
        }
    }

    override suspend fun doWork(): Result {
        if (MainApplication.isSyncRunning.get()) {
            Log.d(TAG, "Sync is running, skipping retry processing")
            return Result.success()
        }

        // Check if already processing
        if (retryQueue.isCurrentlyProcessing()) {
            Log.d(TAG, "Retry queue is already being processed, skipping")
            return Result.success()
        }

        return try {
            retryQueue.setProcessing(true)

            val pendingOperations = retryQueue.getPendingOperations()

            if (pendingOperations.isEmpty()) {
                Log.d(TAG, "No pending retry operations")
                return Result.success()
            }

            Log.i(TAG, "RETRY_QUEUE: Processing ${pendingOperations.size} pending operations")

            var successCount = 0
            var failureCount = 0

            val semaphore = Semaphore(MAX_CONCURRENT_RETRIES)

            // Add timeout for entire batch processing (5 minutes max)
            withTimeout(5 * 60 * 1000L) {
                pendingOperations.chunked(BATCH_SIZE).forEach { batch ->
                    // Check if sync started while we're processing
                    if (MainApplication.isSyncRunning.get()) {
                        Log.d(TAG, "Sync started, pausing retry processing")
                        return@withTimeout
                    }

                    val results = coroutineScope {
                        batch.map { operation ->
                            async {
                                semaphore.withPermit {
                                    processOperation(operation)
                                }
                            }
                        }.awaitAll()
                    }

                    val (batchSuccesses, batchFailures) = results.partition { it }
                    successCount += batchSuccesses.size
                    failureCount += batchFailures.size
                }
            }

            Log.i(TAG, "RETRY_QUEUE: Complete - $successCount succeeded, $failureCount failed")

            retryQueue.cleanup()

            Result.success()
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Retry processing timed out, will continue next cycle")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during retry processing", e)
            Result.retry()
        } finally {
            retryQueue.setProcessing(false)
        }
    }

    private suspend fun processOperation(operation: RetryOperation): Boolean {
        return try {
            // Timeout for individual operation (30 seconds)
            withTimeout(30_000L) {
                when (retryRepository.executeOperation(operation)) {
                    is RetryOperationResult.Success -> true
                    is RetryOperationResult.RetryableFailure,
                    is RetryOperationResult.TerminalFailure -> false
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Operation ${operation.id} timed out")
            retryRepository.markFailed(operation.id, "Timeout", null)
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error for ${operation.id}", e)
            retryRepository.markFailed(operation.id, e.message, null)
            false
        }
    }
}
