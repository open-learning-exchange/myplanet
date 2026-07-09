package org.ole.planet.myplanet.repository

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.model.RealmRetryOperation
import org.ole.planet.myplanet.model.RetryFailure
import org.ole.planet.myplanet.repository.RealmRepository
import org.ole.planet.myplanet.utils.TimeProvider

class RetryRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @RealmDispatcher realmDispatcher: CoroutineDispatcher,
    private val timeProvider: TimeProvider
) : RealmRepository(databaseService, realmDispatcher), RetryRepository {

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
        executeTransaction { realm ->
            RealmRetryOperation.createFromRetryFailure(
                realm, uploadType, failure, payload, endpoint,
                httpMethod, dbId, modelClassName, userId
            )
        }
    }

    override suspend fun updateAttempt(
        operationId: String,
        failure: RetryFailure
    ) {
        executeTransaction { realm ->
            realm.where(RealmRetryOperation::class.java)
                .equalTo("id", operationId)
                .findFirst()?.let { op ->
                    op.attemptCount += 1
                    op.lastAttemptTime = timeProvider.now()
                    op.nextRetryTime = RealmRetryOperation.calculateNextRetryTime(op.attemptCount)
                    op.errorMessage = failure.message
                    op.httpCode = failure.httpCode

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
                    op.lastAttemptTime = timeProvider.now()
                }
        }
    }

    override suspend fun markFailed(operationId: String, errorMessage: String?, httpCode: Int?) {
        executeTransaction { realm ->
            realm.where(RealmRetryOperation::class.java)
                .equalTo("id", operationId)
                .findFirst()?.let { op ->
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
                }
        }
    }

    override suspend fun getPending(): List<RealmRetryOperation> {
        return withRealmAsync { realm ->
            val results = realm.where(RealmRetryOperation::class.java)
                .equalTo("status", RealmRetryOperation.STATUS_PENDING)
                .lessThanOrEqualTo("nextRetryTime", timeProvider.now())
                .rawPredicate("attemptCount < maxAttempts")
                .findAll()

            realm.copyFromRealm(results)
        }
    }

    override suspend fun getPendingCount(): Long {
        return withRealmAsync { realm ->
            realm.where(RealmRetryOperation::class.java)
                .equalTo("status", RealmRetryOperation.STATUS_PENDING)
                .or()
                .equalTo("status", RealmRetryOperation.STATUS_IN_PROGRESS)
                .count()
        }
    }

    override suspend fun cleanup() {
        executeTransaction { realm ->
            val cutoffTime = timeProvider.now() - 24 * 60 * 60 * 1000L
            realm.where(RealmRetryOperation::class.java)
                .equalTo("status", RealmRetryOperation.STATUS_COMPLETED)
                .lessThan("lastAttemptTime", cutoffTime)
                .findAll()
                .deleteAllFromRealm()
        }
    }

    override suspend fun resetAllPending() {
        executeTransaction { realm ->
            realm.where(RealmRetryOperation::class.java)
                .equalTo("status", RealmRetryOperation.STATUS_PENDING)
                .findAll()
                .forEach { op ->
                    op.nextRetryTime = timeProvider.now()
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
                    op.nextRetryTime = timeProvider.now() + 60_000 // Retry in 1 minute
                }
        }
    }
}
