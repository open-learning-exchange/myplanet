package org.ole.planet.myplanet.repository.retry

import javax.inject.Inject
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmRetryOperation
import org.ole.planet.myplanet.repository.RealmRepository
import org.ole.planet.myplanet.services.upload.UploadError

class RetryRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), RetryRepository {

    override suspend fun enqueue(
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
            RealmRetryOperation.createFromUploadError(
                realm, uploadType, error, payload, endpoint,
                httpMethod, dbId, modelClassName, userId
            )
        }
    }

    override suspend fun updateAttempt(
        operationId: String,
        error: UploadError
    ) {
        executeTransaction { realm ->
            realm.where(RealmRetryOperation::class.java)
                .equalTo("id", operationId)
                .findFirst()?.let { op ->
                    op.attemptCount += 1
                    op.lastAttemptTime = System.currentTimeMillis()
                    op.nextRetryTime = RealmRetryOperation.calculateNextRetryTime(op.attemptCount)
                    op.errorMessage = error.message
                    op.httpCode = error.httpCode

                    if (op.attemptCount >= op.maxAttempts) {
                        op.status = RealmRetryOperation.STATUS_ABANDONED
                    }
                }
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
                        op.nextRetryTime = RealmRetryOperation.calculateNextRetryTime(op.attemptCount)
                    }
                }
        }
    }

    override suspend fun getPending(): List<RealmRetryOperation> {
        return withRealmAsync { realm ->
            RealmRetryOperation.getPendingOperations(realm)
        }
    }

    override suspend fun getPendingCount(): Long {
        return withRealmAsync { realm ->
            RealmRetryOperation.getFailedOperationsCount(realm)
        }
    }

    override suspend fun cleanup() {
        executeTransaction { realm ->
            RealmRetryOperation.cleanupCompletedOperations(realm)
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

    override suspend fun getExistingOperation(itemId: String, uploadType: String): RealmRetryOperation? {
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

    override suspend fun deletePendingAndAbandonedOperations() {
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
}
