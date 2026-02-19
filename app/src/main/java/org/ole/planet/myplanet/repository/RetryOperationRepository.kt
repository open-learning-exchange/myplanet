package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmRetryOperation
import org.ole.planet.myplanet.services.upload.UploadError

interface RetryOperationRepository {
    suspend fun findPending(itemId: String, uploadType: String): RealmRetryOperation?
    suspend fun getPendingOperations(): List<RealmRetryOperation>
    suspend fun getPendingCount(): Long
    suspend fun addOperation(
        uploadType: String,
        error: UploadError,
        payload: String,
        endpoint: String,
        httpMethod: String,
        dbId: String?,
        modelClassName: String,
        userId: String?
    )
    suspend fun updateOperation(operation: RealmRetryOperation)
    suspend fun markInProgress(id: String)
    suspend fun markCompleted(id: String)
    suspend fun markFailed(id: String, errorMessage: String?, httpCode: Int?)
    suspend fun cleanupCompleted()
    suspend fun resetAllPending()
    suspend fun clearQueue()
    suspend fun recoverStuck()
}
