package org.ole.planet.myplanet.repository

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.ole.planet.myplanet.data.room.dao.RetryDao
import org.ole.planet.myplanet.model.RetryFailure
import org.ole.planet.myplanet.model.RetryOperation
import org.ole.planet.myplanet.utils.TimeProvider

class RetryRepositoryImpl @Inject constructor(
    private val retryDao: RetryDao,
    private val timeProvider: TimeProvider
) : RetryRepository {

    private val isProcessing = AtomicBoolean(false)
    private val mutex = Mutex()

    override suspend fun enqueue(
        uploadType: String,
        failure: RetryFailure,
        payload: String,
        endpoint: String,
        httpMethod: String,
        dbId: String?,
        modelClassName: String,
        userId: String?
    ) {
        val operation = RetryOperation.createFromRetryFailure(
            uploadType, failure, payload, endpoint,
            httpMethod, dbId, modelClassName, userId
        )
        retryDao.insert(operation)
    }

    override suspend fun updateAttempt(
        operationId: String,
        failure: RetryFailure
    ) {
        retryDao.findById(operationId)?.let { op ->
            op.attemptCount += 1
            op.lastAttemptTime = timeProvider.now()
            op.nextRetryTime = RetryOperation.calculateNextRetryTime(op.attemptCount)
            op.errorMessage = failure.message
            op.httpCode = failure.httpCode

            if (op.attemptCount >= op.maxAttempts) {
                op.status = RetryOperation.STATUS_ABANDONED
            }
            retryDao.update(op)
        }
    }

    override suspend fun markInProgress(operationId: String) {
        retryDao.markInProgress(operationId)
    }

    override suspend fun markCompleted(operationId: String) {
        retryDao.markCompleted(operationId, timeProvider.now())
    }

    override suspend fun markFailed(operationId: String, errorMessage: String?, httpCode: Int?) {
        retryDao.findById(operationId)?.let { op ->
            op.attemptCount += 1
            op.lastAttemptTime = timeProvider.now()
            op.errorMessage = errorMessage
            op.httpCode = httpCode

            if (op.attemptCount >= op.maxAttempts) {
                op.status = RetryOperation.STATUS_ABANDONED
            } else {
                op.status = RetryOperation.STATUS_PENDING
                op.nextRetryTime = RetryOperation.calculateNextRetryTime(op.attemptCount)
            }
            retryDao.update(op)
        }
    }

    override suspend fun getPending(): List<RetryOperation> {
        return retryDao.getPending(timeProvider.now())
    }

    override suspend fun getPendingCount(): Long {
        return retryDao.getActiveCount()
    }

    override suspend fun cleanup() {
        val cutoffTime = timeProvider.now() - 24 * 60 * 60 * 1000L
        retryDao.deleteOldCompleted(cutoffTime)
    }

    override suspend fun getExistingOperation(itemId: String, uploadType: String): RetryOperation? {
        return retryDao.findExisting(itemId, uploadType)
    }

    override suspend fun deletePendingAndAbandonedOperations() {
        retryDao.deletePendingAndAbandoned()
    }

    override suspend fun recoverStuckOperations() {
        retryDao.recoverStuck(timeProvider.now() + 60_000)
    }

    override fun isCurrentlyProcessing(): Boolean = isProcessing.get()

    override fun setProcessing(processing: Boolean) {
        isProcessing.set(processing)
    }

    override suspend fun safeClearQueue(): Boolean {
        if (isProcessing.get()) {
            return false
        }

        return mutex.withLock {
            if (isProcessing.get()) {
                return@withLock false
            }

            deletePendingAndAbandonedOperations()
            true
        }
    }

    override suspend fun getRetryQueueSnapshot(): RetryQueueDetails {
        val pendingCount = getPendingCount()
        val pendingOps = getPending()
        val isProcessing = isCurrentlyProcessing()
        return RetryQueueDetails(pendingCount, pendingOps, isProcessing)
    }
}
