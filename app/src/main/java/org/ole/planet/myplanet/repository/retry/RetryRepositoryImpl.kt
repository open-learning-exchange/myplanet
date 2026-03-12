package org.ole.planet.myplanet.repository.retry

import java.util.UUID
import javax.inject.Inject
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmRetryOperation
import org.ole.planet.myplanet.repository.RealmRepository
import org.ole.planet.myplanet.services.upload.UploadError

class RetryRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), RetryRepository {

    override suspend fun getPendingOperations(): List<RealmRetryOperation> {
        return queryList(RealmRetryOperation::class.java) {
            equalTo("status", RealmRetryOperation.STATUS_PENDING)
            lessThanOrEqualTo("nextRetryTime", System.currentTimeMillis())
        }.filter { it.attemptCount < it.maxAttempts }
    }

    override suspend fun getPendingCount(): Long {
        return count(RealmRetryOperation::class.java) {
            equalTo("status", RealmRetryOperation.STATUS_PENDING)
        }
    }

    override suspend fun getFailedOperationsCount(): Long {
        return count(RealmRetryOperation::class.java) {
            equalTo("status", RealmRetryOperation.STATUS_PENDING)
            or()
            equalTo("status", RealmRetryOperation.STATUS_IN_PROGRESS)
        }
    }

    override suspend fun cleanupCompletedOperations(olderThanMs: Long) {
        val cutoffTime = System.currentTimeMillis() - olderThanMs
        executeTransaction { realm ->
            realm.where(RealmRetryOperation::class.java)
                .equalTo("status", RealmRetryOperation.STATUS_COMPLETED)
                .lessThan("lastAttemptTime", cutoffTime)
                .findAll()
                .deleteAllFromRealm()
        }
    }

    override suspend fun getExistingOperation(uploadType: String, itemId: String): RealmRetryOperation? {
        return withRealmAsync { realm ->
            realm.where(RealmRetryOperation::class.java)
                .equalTo("itemId", itemId)
                .equalTo("uploadType", uploadType)
                .notEqualTo("status", RealmRetryOperation.STATUS_COMPLETED)
                .notEqualTo("status", RealmRetryOperation.STATUS_ABANDONED)
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun updateExistingOperation(
        operationId: String,
        error: UploadError
    ) {
        executeTransaction { realm ->
            realm.where(RealmRetryOperation::class.java)
                .equalTo("id", operationId)
                .findFirst()?.let { op ->
                    op.attemptCount += 1
                    op.lastAttemptTime = System.currentTimeMillis()
                    op.nextRetryTime = calculateNextRetryTime(op.attemptCount)
                    op.errorMessage = error.message
                    op.httpCode = error.httpCode

                    if (op.attemptCount >= op.maxAttempts) {
                        op.status = RealmRetryOperation.STATUS_ABANDONED
                    }
                }
        }
    }

    override suspend fun createNewOperation(
        uploadType: String,
        error: UploadError,
        payload: String,
        endpoint: String,
        httpMethod: String,
        dbId: String?,
        modelClassName: String,
        userId: String?
    ) {
        executeTransaction { realm ->
            val operation = realm.createObject(
                RealmRetryOperation::class.java,
                UUID.randomUUID().toString()
            )
            operation.uploadType = uploadType
            operation.itemId = error.itemId
            operation.serializedPayload = payload
            operation.endpoint = endpoint
            operation.httpMethod = httpMethod
            operation.dbId = dbId
            operation.status = RealmRetryOperation.STATUS_PENDING
            operation.attemptCount = 1
            operation.createdTime = System.currentTimeMillis()
            operation.lastAttemptTime = System.currentTimeMillis()
            operation.nextRetryTime = calculateNextRetryTime(1)
            operation.errorMessage = error.message
            operation.httpCode = error.httpCode
            operation.modelClassName = modelClassName
            operation.userId = userId
        }
    }

    override suspend fun markInProgress(operationId: String) {
        executeTransaction { realm ->
            realm.where(RealmRetryOperation::class.java)
                .equalTo("id", operationId)
                .findFirst()?.let { op ->
                    op.status = RealmRetryOperation.STATUS_IN_PROGRESS
                }
        }
    }

    override suspend fun markCompleted(operationId: String) {
        executeTransaction { realm ->
            realm.where(RealmRetryOperation::class.java)
                .equalTo("id", operationId)
                .findFirst()?.let { op ->
                    op.status = RealmRetryOperation.STATUS_COMPLETED
                    op.lastAttemptTime = System.currentTimeMillis()
                }
        }
    }

    override suspend fun markFailed(operationId: String, errorMessage: String?, httpCode: Int?) {
        executeTransaction { realm ->
            realm.where(RealmRetryOperation::class.java)
                .equalTo("id", operationId)
                .findFirst()?.let { op ->
                    op.attemptCount += 1
                    op.lastAttemptTime = System.currentTimeMillis()
                    op.errorMessage = errorMessage
                    op.httpCode = httpCode

                    if (op.attemptCount >= op.maxAttempts) {
                        op.status = RealmRetryOperation.STATUS_ABANDONED
                    } else {
                        op.status = RealmRetryOperation.STATUS_PENDING
                        op.nextRetryTime = calculateNextRetryTime(op.attemptCount)
                    }
                }
        }
    }

    override suspend fun resetAllPending() {
        executeTransaction { realm ->
            realm.where(RealmRetryOperation::class.java)
                .equalTo("status", RealmRetryOperation.STATUS_PENDING)
                .findAll()
                .forEach { op ->
                    op.nextRetryTime = System.currentTimeMillis()
                }
        }
    }

    override suspend fun clearPendingAndAbandonedOperations() {
        executeTransaction { realm ->
            realm.where(RealmRetryOperation::class.java)
                .equalTo("status", RealmRetryOperation.STATUS_PENDING)
                .or()
                .equalTo("status", RealmRetryOperation.STATUS_ABANDONED)
                .findAll()
                .deleteAllFromRealm()
        }
    }

    override suspend fun recoverStuckOperations() {
        executeTransaction { realm ->
            realm.where(RealmRetryOperation::class.java)
                .equalTo("status", RealmRetryOperation.STATUS_IN_PROGRESS)
                .findAll()
                .forEach { op ->
                    op.status = RealmRetryOperation.STATUS_PENDING
                    op.nextRetryTime = System.currentTimeMillis() + 60_000 // Retry in 1 minute
                }
        }
    }

    companion object {
        private const val BASE_DELAY_MS = 30_000L
        private const val MAX_DELAY_MS = 30 * 60 * 1000L

        fun calculateNextRetryTime(attemptCount: Int): Long {
            val delay = minOf(BASE_DELAY_MS * (1L shl attemptCount), MAX_DELAY_MS)
            return System.currentTimeMillis() + delay
        }
    }
}
