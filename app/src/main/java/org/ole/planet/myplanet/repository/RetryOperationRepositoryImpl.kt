package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmRetryOperation
import org.ole.planet.myplanet.services.upload.UploadError

class RetryOperationRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), RetryOperationRepository {

    override suspend fun findPending(itemId: String, uploadType: String): RealmRetryOperation? {
        return queryList(RealmRetryOperation::class.java) {
            equalTo("itemId", itemId)
            equalTo("uploadType", uploadType)
            notEqualTo("status", RealmRetryOperation.STATUS_COMPLETED)
            notEqualTo("status", RealmRetryOperation.STATUS_ABANDONED)
        }.firstOrNull()
    }

    override suspend fun getPendingOperations(): List<RealmRetryOperation> {
        return withRealm { realm ->
            RealmRetryOperation.getPendingOperations(realm)
        }
    }

    override suspend fun getPendingCount(): Long {
        return withRealm { realm ->
            RealmRetryOperation.getFailedOperationsCount(realm)
        }
    }

    override suspend fun addOperation(
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

    override suspend fun updateOperation(operation: RealmRetryOperation) {
        save(operation)
    }

    override suspend fun markInProgress(id: String) {
        update(RealmRetryOperation::class.java, "id", id) { op ->
            op.status = RealmRetryOperation.STATUS_IN_PROGRESS
        }
    }

    override suspend fun markCompleted(id: String) {
        update(RealmRetryOperation::class.java, "id", id) { op ->
            op.status = RealmRetryOperation.STATUS_COMPLETED
            op.lastAttemptTime = System.currentTimeMillis()
        }
    }

    override suspend fun markFailed(id: String, errorMessage: String?, httpCode: Int?) {
        update(RealmRetryOperation::class.java, "id", id) { op ->
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

    override suspend fun cleanupCompleted() {
        executeTransaction { realm ->
            RealmRetryOperation.cleanupCompletedOperations(realm)
        }
    }

    override suspend fun resetAllPending() {
        executeTransaction { realm ->
            val pending = realm.where(RealmRetryOperation::class.java)
                .equalTo("status", RealmRetryOperation.STATUS_PENDING)
                .findAll()
            for (op in pending) {
                op.nextRetryTime = System.currentTimeMillis()
            }
        }
    }

    override suspend fun clearQueue() {
        executeTransaction { realm ->
            realm.where(RealmRetryOperation::class.java)
                .equalTo("status", RealmRetryOperation.STATUS_PENDING)
                .or()
                .equalTo("status", RealmRetryOperation.STATUS_ABANDONED)
                .findAll()
                .deleteAllFromRealm()
        }
    }

    override suspend fun recoverStuck() {
        executeTransaction { realm ->
            val stuck = realm.where(RealmRetryOperation::class.java)
                .equalTo("status", RealmRetryOperation.STATUS_IN_PROGRESS)
                .findAll()
            for (op in stuck) {
                op.status = RealmRetryOperation.STATUS_PENDING
                op.nextRetryTime = System.currentTimeMillis() + 60_000
            }
        }
    }
}
