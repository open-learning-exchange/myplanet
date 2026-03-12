package org.ole.planet.myplanet.repository.retry

import org.ole.planet.myplanet.model.RealmRetryOperation
import org.ole.planet.myplanet.services.upload.UploadError

interface RetryRepository {
    suspend fun getPendingOperations(): List<RealmRetryOperation>
    suspend fun getPendingCount(): Long
    suspend fun getFailedOperationsCount(): Long
    suspend fun cleanupCompletedOperations(olderThanMs: Long = 24 * 60 * 60 * 1000L)
    suspend fun getExistingOperation(uploadType: String, itemId: String): RealmRetryOperation?
    suspend fun updateExistingOperation(
        operationId: String,
        error: UploadError
    )
    suspend fun createNewOperation(
        uploadType: String,
        error: UploadError,
        payload: String,
        endpoint: String,
        httpMethod: String,
        dbId: String?,
        modelClassName: String,
        userId: String?
    )
    suspend fun markInProgress(operationId: String)
    suspend fun markCompleted(operationId: String)
    suspend fun markFailed(operationId: String, errorMessage: String?, httpCode: Int?)
    suspend fun resetAllPending()
    suspend fun clearPendingAndAbandonedOperations()
    suspend fun recoverStuckOperations()
}
