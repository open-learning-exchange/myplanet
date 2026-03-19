package org.ole.planet.myplanet.repository.retry

import org.ole.planet.myplanet.model.RealmRetryOperation
import org.ole.planet.myplanet.services.upload.UploadError

interface RetryRepository {
    suspend fun enqueue(
        uploadType: String,
        error: UploadError,
        payload: String,
        endpoint: String,
        httpMethod: String,
        dbId: String?,
        modelClassName: String,
        userId: String?
    )
    suspend fun updateAttempt(
        operationId: String,
        error: UploadError
    )
    suspend fun markInProgress(operationId: String)
    suspend fun markCompleted(operationId: String)
    suspend fun markFailed(operationId: String, errorMessage: String?, httpCode: Int?)
    suspend fun getPending(): List<RealmRetryOperation>
    suspend fun getPendingCount(): Long
    suspend fun cleanup()
    suspend fun resetAllPending()
    suspend fun getExistingOperation(itemId: String, uploadType: String): RealmRetryOperation?
    suspend fun deletePendingAndAbandonedOperations()
    suspend fun recoverStuckOperations()
}
