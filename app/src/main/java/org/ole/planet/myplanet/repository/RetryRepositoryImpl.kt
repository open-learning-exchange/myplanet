package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.data.room.dao.RetryDao
import org.ole.planet.myplanet.model.RealmRetryOperation
import org.ole.planet.myplanet.model.RetryFailure
import org.ole.planet.myplanet.utils.TimeProvider

class RetryRepositoryImpl @Inject constructor(
    private val retryDao: RetryDao,
    private val timeProvider: TimeProvider
) : RetryRepository {

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
        val operation = RealmRetryOperation.createFromRetryFailure(
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
            op.nextRetryTime = RealmRetryOperation.calculateNextRetryTime(op.attemptCount)
            op.errorMessage = failure.message
            op.httpCode = failure.httpCode

            if (op.attemptCount >= op.maxAttempts) {
                op.status = RealmRetryOperation.STATUS_ABANDONED
            }
            retryDao.update(op)
        }
    }

    override suspend fun markInProgress(operationId: String) {
        retryDao.findById(operationId)?.let { op ->
            op.status = RealmRetryOperation.STATUS_IN_PROGRESS
            retryDao.update(op)
        }
    }

    override suspend fun markCompleted(operationId: String) {
        retryDao.findById(operationId)?.let { op ->
            op.status = RealmRetryOperation.STATUS_COMPLETED
            op.lastAttemptTime = timeProvider.now()
            retryDao.update(op)
        }
    }

    override suspend fun markFailed(operationId: String, errorMessage: String?, httpCode: Int?) {
        retryDao.findById(operationId)?.let { op ->
            op.attemptCount += 1
            op.lastAttemptTime = timeProvider.now()
            op.errorMessage = errorMessage
            op.httpCode = httpCode

            if (op.attemptCount >= op.maxAttempts) {
                op.status = RealmRetryOperation.STATUS_ABANDONED
            } else {
                op.status = RealmRetryOperation.STATUS_PENDING
                op.nextRetryTime = RealmRetryOperation.calculateNextRetryTime(op.attemptCount)
            }
            retryDao.update(op)
        }
    }

    override suspend fun getPending(): List<RealmRetryOperation> {
        return retryDao.getPending(timeProvider.now())
    }

    override suspend fun getPendingCount(): Long {
        return retryDao.getActiveCount()
    }

    override suspend fun cleanup() {
        val cutoffTime = timeProvider.now() - 24 * 60 * 60 * 1000L
        retryDao.deleteOldCompleted(cutoffTime)
    }

    override suspend fun resetAllPending() {
        retryDao.resetPendingRetryTime(timeProvider.now())
    }

    override suspend fun getExistingOperation(itemId: String, uploadType: String): RealmRetryOperation? {
        return retryDao.findExisting(itemId, uploadType)
    }

    override suspend fun deletePendingAndAbandonedOperations() {
        retryDao.deletePendingAndAbandoned()
    }

    override suspend fun recoverStuckOperations() {
        retryDao.recoverStuck(timeProvider.now() + 60_000)
    }
}
