package org.ole.planet.myplanet.services.retry

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.google.gson.JsonParser
import dagger.hilt.android.EntryPointAccessors
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.ole.planet.myplanet.di.RetryQueueEntryPoint
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.model.RealmRetryOperation
import org.ole.planet.myplanet.utils.UrlUtils

class RetryQueueWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "RetryQueueWorker"
        private const val WORK_NAME = "retryQueueWork"
        private const val BATCH_SIZE = 50

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<RetryQueueWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
            Log.d(TAG, "Scheduled RetryQueueWorker")
        }

        fun triggerImmediateRetry(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<RetryQueueWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
            Log.d(TAG, "Triggered immediate retry")
        }
    }

    override suspend fun doWork(): Result {
        if (MainApplication.isSyncRunning) {
            Log.d(TAG, "Sync is running, skipping retry processing")
            return Result.success()
        }

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            RetryQueueEntryPoint::class.java
        )

        val retryQueue = entryPoint.retryQueue()
        val apiInterface = entryPoint.apiInterface()

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

            // Add timeout for entire batch processing (5 minutes max)
            withTimeout(5 * 60 * 1000L) {
                pendingOperations.chunked(BATCH_SIZE).forEach { batch ->
                    // Check if sync started while we're processing
                    if (MainApplication.isSyncRunning) {
                        Log.d(TAG, "Sync started, pausing retry processing")
                        return@withTimeout
                    }

                    batch.forEach { operation ->
                        val success = processOperation(operation, apiInterface, retryQueue)
                        if (success) successCount++ else failureCount++
                    }
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

    private suspend fun processOperation(
        operation: RealmRetryOperation,
        apiInterface: org.ole.planet.myplanet.data.api.ApiInterface,
        retryQueue: RetryQueue
    ): Boolean {
        return try {
            // Timeout for individual operation (30 seconds)
            withTimeout(30_000L) {
                processOperationInternal(operation, apiInterface, retryQueue)
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Operation ${operation.id} timed out")
            retryQueue.markFailed(operation.id, "Timeout", null)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error for ${operation.id}", e)
            retryQueue.markFailed(operation.id, e.message, null)
            false
        }
    }

    private suspend fun processOperationInternal(
        operation: RealmRetryOperation,
        apiInterface: org.ole.planet.myplanet.data.api.ApiInterface,
        retryQueue: RetryQueue
    ): Boolean {
        return try {
            retryQueue.markInProgress(operation.id)

            val payload = try {
                JsonParser.parseString(operation.serializedPayload).asJsonObject
            } catch (e: Exception) {
                Log.e(TAG, "Invalid payload for ${operation.id}, abandoning")
                retryQueue.markFailed(operation.id, "Invalid payload", null)
                return false
            }
            val requestUrl = if (operation.dbId.isNullOrEmpty()) {
                "${UrlUtils.getUrl()}/${operation.endpoint}"
            } else {
                "${UrlUtils.getUrl()}/${operation.endpoint}/${operation.dbId}"
            }

            val response = if (operation.httpMethod == "PUT" && !operation.dbId.isNullOrEmpty()) {
                apiInterface.putDocSuspend(
                    UrlUtils.header,
                    "application/json",
                    requestUrl,
                    payload
                )
            } else {
                apiInterface.postDocSuspend(
                    UrlUtils.header,
                    "application/json",
                    requestUrl,
                    payload
                )
            }

            if (response.isSuccessful) {
                retryQueue.markCompleted(operation.id)
                Log.d(TAG, "Successfully retried operation ${operation.id}")
                true
            } else if (response.code() == 409) {
                // 409 Conflict means document already exists - data is already synced
                retryQueue.markCompleted(operation.id)
                Log.d(TAG, "Operation ${operation.id} already synced (409 conflict)")
                true
            } else {
                val isRetryable = response.code() >= 500
                if (isRetryable) {
                    retryQueue.markFailed(
                        operation.id,
                        "HTTP ${response.code()}",
                        response.code()
                    )
                } else {
                    retryQueue.markFailed(
                        operation.id,
                        "Non-retryable HTTP ${response.code()}",
                        response.code()
                    )
                }
                Log.w(TAG, "Retry failed for ${operation.id}: HTTP ${response.code()}")
                false
            }
        } catch (e: IOException) {
            retryQueue.markFailed(operation.id, e.message, null)
            Log.w(TAG, "Network error during retry for ${operation.id}", e)
            false
        } catch (e: Exception) {
            retryQueue.markFailed(operation.id, e.message, null)
            Log.e(TAG, "Unexpected error during retry for ${operation.id}", e)
            false
        }
    }
}
