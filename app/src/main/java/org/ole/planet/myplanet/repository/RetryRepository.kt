package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmRetryOperation
import org.ole.planet.myplanet.model.RetryFailure

interface RetryRepository {
    suspend fun enqueue(
        uploadType: String,
        failure: RetryFailure,
        payload: String,
        endpoint: String,
        httpMethod: String,
        dbId: String?,
        modelClassName: String,
        userId: String?
    )
    suspend fun updateAttempt(
        operationId: String,
        failure: RetryFailure
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
