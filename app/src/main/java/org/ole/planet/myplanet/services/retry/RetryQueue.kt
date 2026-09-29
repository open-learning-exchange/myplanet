package org.ole.planet.myplanet.services.retry

import android.util.Log
import com.google.gson.JsonObject
import javax.inject.Inject
import javax.inject.Singleton
import org.ole.planet.myplanet.model.RetryFailure
import org.ole.planet.myplanet.model.RetryOperation
import org.ole.planet.myplanet.repository.RetryRepository
import org.ole.planet.myplanet.services.upload.UploadError

@Singleton
class RetryQueue @Inject constructor(
    private val retryRepository: RetryRepository
) {
    companion object {
        private const val TAG = "RetryQueue"
    }

    fun isCurrentlyProcessing(): Boolean = retryRepository.isCurrentlyProcessing()

    internal fun setProcessing(processing: Boolean) {
        retryRepository.setProcessing(processing)
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

        val failure = RetryFailure(error.itemId, error.message, error.httpCode)
        val existingOperation = retryRepository.getExistingOperation(error.itemId, uploadType)

        if (existingOperation != null) {
            retryRepository.updateAttempt(existingOperation.id, failure)
            Log.d(TAG, "Updated existing retry operation for item ${error.itemId}")
        } else {
            retryRepository.enqueue(
                uploadType, failure, payload.toString(), endpoint,
                httpMethod, dbId, modelClassName, userId
            )
            Log.i(TAG, "RETRY_QUEUE: Queued new operation - type=$uploadType, itemId=${error.itemId}, error=${error.message}")
        }
    }

    suspend fun getPendingOperations(): List<RetryOperation> {
        return retryRepository.getPending()
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

    /**
     * Safely clear all pending/abandoned operations.
     * Returns false if processing is active (cannot clear).
     */
    suspend fun safeClearQueue(): Boolean {
        return retryRepository.safeClearQueue()
    }

    /**
     * Reset any stuck in_progress items back to pending.
     * Called on app startup to recover from crashes.
     */
    suspend fun recoverStuckOperations() {
        retryRepository.recoverStuckOperations()
    }
}
