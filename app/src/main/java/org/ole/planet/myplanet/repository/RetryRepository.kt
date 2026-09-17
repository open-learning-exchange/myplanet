package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RetryFailure
import org.ole.planet.myplanet.model.RetryOperation

sealed class RetryOperationResult {
    object Success : RetryOperationResult()
    data class RetryableFailure(val message: String?, val httpCode: Int? = null) : RetryOperationResult()
    data class TerminalFailure(val message: String?, val httpCode: Int? = null) : RetryOperationResult()
}

data class RetryQueueDetails(val pendingCount: Long = 0, val pendingOps: List<RetryOperation> = emptyList(), val isProcessing: Boolean = false)

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
    suspend fun executeOperation(operation: RetryOperation): RetryOperationResult
    suspend fun getPending(): List<RetryOperation>
    suspend fun getPendingCount(): Long
    suspend fun cleanup()
    suspend fun getExistingOperation(itemId: String, uploadType: String): RetryOperation?
    suspend fun deletePendingAndAbandonedOperations()
    suspend fun recoverStuckOperations()
    fun isCurrentlyProcessing(): Boolean
    fun setProcessing(processing: Boolean)
    suspend fun safeClearQueue(): Boolean
    suspend fun getRetryQueueSnapshot(): RetryQueueDetails
}
