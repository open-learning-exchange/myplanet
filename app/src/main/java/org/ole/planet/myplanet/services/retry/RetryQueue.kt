package org.ole.planet.myplanet.services.retry

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.ole.planet.myplanet.model.RealmRetryOperation
import org.ole.planet.myplanet.repository.RetryRepository
import org.ole.planet.myplanet.services.upload.UploadError

@Singleton
class RetryQueue @Inject constructor(
    private val retryRepository: RetryRepository,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "RetryQueue"
    }

    private val isProcessing = AtomicBoolean(false)
    private val mutex = Mutex()

    fun isCurrentlyProcessing(): Boolean = isProcessing.get()

    internal fun setProcessing(processing: Boolean) {
        isProcessing.set(processing)
    }

    suspend fun queueFailedOperation(
        uploadType: String,
        error: UploadError,
        payload: JsonObject,
        endpoint: String,
        httpMethod: String = "POST",
        dbId: String? = null,
        modelClassName: String,
        userId: String? = null
    ) {
        if (!error.retryable) {
            Log.d(TAG, "Skipping non-retryable error for item ${error.itemId}: ${error.message}")
            return
        }

        val existingOperation = retryRepository.getExistingOperation(error.itemId, uploadType)

        if (existingOperation != null) {
            retryRepository.updateAttempt(existingOperation.id, error)
            Log.d(TAG, "Updated existing retry operation for item ${error.itemId}")
        } else {
            retryRepository.enqueue(
                uploadType, error, payload.toString(), endpoint,
                httpMethod, dbId, modelClassName, userId
            )
            Log.i(TAG, "RETRY_QUEUE: Queued new operation - type=$uploadType, itemId=${error.itemId}, error=${error.message}")
        }
    }

    suspend fun queueFailedOperations(
        uploadType: String,
        errors: List<UploadError>,
        payloadProvider: (String) -> JsonObject?,
        endpoint: String,
        httpMethod: String = "POST",
        dbIdProvider: ((String) -> String?)? = null,
        modelClassName: String,
        userId: String? = null
    ) {
        errors.filter { it.retryable }.forEach { error ->
            val payload = payloadProvider(error.itemId)
            if (payload != null) {
                queueFailedOperation(
                    uploadType, error, payload, endpoint, httpMethod,
                    dbIdProvider?.invoke(error.itemId), modelClassName, userId
                )
            } else {
                Log.w(TAG, "Could not retrieve payload for item ${error.itemId}, skipping queue")
            }
        }
    }

    suspend fun getPendingOperations(): List<RealmRetryOperation> {
        return retryRepository.getPending()
    }

    suspend fun getPendingCount(): Long {
        return retryRepository.getPendingCount()
    }

    suspend fun markInProgress(operationId: String) {
        retryRepository.markInProgress(operationId)
    }

    suspend fun markCompleted(operationId: String) {
        retryRepository.markCompleted(operationId)
        Log.d(TAG, "Marked operation $operationId as completed")
    }

    suspend fun markFailed(operationId: String, errorMessage: String?, httpCode: Int?) {
        retryRepository.markFailed(operationId, errorMessage, httpCode)
    }

    suspend fun cleanup() {
        retryRepository.cleanup()
    }

    suspend fun resetAllPending() {
        retryRepository.resetAllPending()
    }

    /**
     * Safely clear all pending/abandoned operations.
     * Returns false if processing is active (cannot clear).
     */
    suspend fun safeClearQueue(): Boolean {
        if (isProcessing.get()) {
            Log.w(TAG, "Cannot clear queue while processing is active")
            return false
        }

        return mutex.withLock {
            if (isProcessing.get()) {
                Log.w(TAG, "Cannot clear queue while processing is active")
                return@withLock false
            }

            retryRepository.deletePendingAndAbandonedOperations()
            Log.i(TAG, "Queue cleared successfully")
            true
        }
    }

    /**
     * Reset any stuck in_progress items back to pending.
     * Called on app startup to recover from crashes.
     */
    suspend fun recoverStuckOperations() {
        retryRepository.recoverStuckOperations()
    }
}
