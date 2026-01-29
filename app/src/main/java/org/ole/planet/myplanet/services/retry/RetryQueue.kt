package org.ole.planet.myplanet.services.retry

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmRetryOperation
import org.ole.planet.myplanet.services.upload.UploadError

@Singleton
class RetryQueue @Inject constructor(
    private val databaseService: DatabaseService,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "RetryQueue"
    }

    private val isProcessing = AtomicBoolean(false)
    private val mutex = Mutex()

    fun isCurrentlyProcessing(): Boolean = isProcessing.get()

    fun setProcessing(processing: Boolean) {
        isProcessing.set(processing)
    }

    suspend fun queueFailedOperation(
        uploadType: String,
        error: UploadError,
        payload: JsonObject,
        endpoint: String,
        httpMethod: String = "POST",
        dbId: String? = null,
        modelClassName: String,
        userId: String? = null
    ) {
        if (!error.retryable) {
            Log.d(TAG, "Skipping non-retryable error for item ${error.itemId}: ${error.message}")
            return
        }

        val existingOperation = databaseService.withRealmAsync { realm ->
            realm.where(RealmRetryOperation::class.java)
                .equalTo("itemId", error.itemId)
                .equalTo("uploadType", uploadType)
                .notEqualTo("status", RealmRetryOperation.STATUS_COMPLETED)
                .notEqualTo("status", RealmRetryOperation.STATUS_ABANDONED)
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
        }

        if (existingOperation != null) {
            databaseService.executeTransactionAsync { realm ->
                realm.where(RealmRetryOperation::class.java)
                    .equalTo("id", existingOperation.id)
                    .findFirst()?.let { op ->
                        op.attemptCount += 1
                        op.lastAttemptTime = System.currentTimeMillis()
                        op.nextRetryTime = RealmRetryOperation.calculateNextRetryTime(op.attemptCount)
                        op.errorMessage = error.message
                        op.httpCode = error.httpCode

                        if (op.attemptCount >= op.maxAttempts) {
                            op.status = RealmRetryOperation.STATUS_ABANDONED
                            Log.w(TAG, "Operation ${op.id} abandoned after ${op.maxAttempts} attempts")
                        }
                    }
            }
            Log.d(TAG, "Updated existing retry operation for item ${error.itemId}")
        } else {
            databaseService.executeTransactionAsync { realm ->
                RealmRetryOperation.createFromUploadError(
                    realm, uploadType, error, payload.toString(), endpoint,
                    httpMethod, dbId, modelClassName, userId
                )
            }
            Log.i(TAG, "RETRY_QUEUE: Queued new operation - type=$uploadType, itemId=${error.itemId}, error=${error.message}")
        }
    }

    suspend fun queueFailedOperations(
        uploadType: String,
        errors: List<UploadError>,
        payloadProvider: (String) -> JsonObject?,
        endpoint: String,
        httpMethod: String = "POST",
        dbIdProvider: ((String) -> String?)? = null,
        modelClassName: String,
        userId: String? = null
    ) {
        errors.filter { it.retryable }.forEach { error ->
            val payload = payloadProvider(error.itemId)
            if (payload != null) {
                queueFailedOperation(
                    uploadType, error, payload, endpoint, httpMethod,
                    dbIdProvider?.invoke(error.itemId), modelClassName, userId
                )
            } else {
                Log.w(TAG, "Could not retrieve payload for item ${error.itemId}, skipping queue")
            }
        }
    }

    suspend fun getPendingOperations(): List<RealmRetryOperation> {
        return databaseService.withRealmAsync { realm ->
            RealmRetryOperation.getPendingOperations(realm)
        }
    }

    suspend fun getPendingCount(): Long {
        return databaseService.withRealmAsync { realm ->
            RealmRetryOperation.getFailedOperationsCount(realm)
        }
    }

    suspend fun markInProgress(operationId: String) {
        databaseService.executeTransactionAsync { realm ->
            realm.where(RealmRetryOperation::class.java)
                .equalTo("id", operationId)
                .findFirst()?.let { op ->
                    op.status = RealmRetryOperation.STATUS_IN_PROGRESS
                }
        }
    }

    suspend fun markCompleted(operationId: String) {
        databaseService.executeTransactionAsync { realm ->
            realm.where(RealmRetryOperation::class.java)
                .equalTo("id", operationId)
                .findFirst()?.let { op ->
                    op.status = RealmRetryOperation.STATUS_COMPLETED
                    op.lastAttemptTime = System.currentTimeMillis()
                }
        }
        Log.d(TAG, "Marked operation $operationId as completed")
    }

    suspend fun markFailed(operationId: String, errorMessage: String?, httpCode: Int?) {
        databaseService.executeTransactionAsync { realm ->
            realm.where(RealmRetryOperation::class.java)
                .equalTo("id", operationId)
                .findFirst()?.let { op ->
                    op.attemptCount += 1
                    op.lastAttemptTime = System.currentTimeMillis()
                    op.errorMessage = errorMessage
                    op.httpCode = httpCode

                    if (op.attemptCount >= op.maxAttempts) {
                        op.status = RealmRetryOperation.STATUS_ABANDONED
                        Log.w(TAG, "Operation $operationId abandoned after ${op.maxAttempts} attempts")
                    } else {
                        op.status = RealmRetryOperation.STATUS_PENDING
                        op.nextRetryTime = RealmRetryOperation.calculateNextRetryTime(op.attemptCount)
                    }
                }
        }
    }

    suspend fun cleanup() {
        databaseService.executeTransactionAsync { realm ->
            RealmRetryOperation.cleanupCompletedOperations(realm)
        }
    }

    suspend fun resetAllPending() {
        databaseService.executeTransactionAsync { realm ->
            realm.where(RealmRetryOperation::class.java)
                .equalTo("status", RealmRetryOperation.STATUS_PENDING)
                .findAll()
                .forEach { op ->
                    op.nextRetryTime = System.currentTimeMillis()
                }
        }
    }

    /**
     * Safely clear all pending/abandoned operations.
     * Returns false if processing is active (cannot clear).
     */
    suspend fun safeClearQueue(): Boolean {
        if (isProcessing.get()) {
            Log.w(TAG, "Cannot clear queue while processing is active")
            return false
        }

        return mutex.withLock {
            if (isProcessing.get()) {
                Log.w(TAG, "Cannot clear queue while processing is active")
                return@withLock false
            }

            databaseService.executeTransactionAsync { realm ->
                // Only delete pending and abandoned, not in_progress or completed
                realm.where(RealmRetryOperation::class.java)
                    .equalTo("status", RealmRetryOperation.STATUS_PENDING)
                    .or()
                    .equalTo("status", RealmRetryOperation.STATUS_ABANDONED)
                    .findAll()
                    .deleteAllFromRealm()
            }
            Log.i(TAG, "Queue cleared successfully")
            true
        }
    }

    /**
     * Reset any stuck in_progress items back to pending.
     * Called on app startup to recover from crashes.
     */
    suspend fun recoverStuckOperations() {
        databaseService.executeTransactionAsync { realm ->
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
