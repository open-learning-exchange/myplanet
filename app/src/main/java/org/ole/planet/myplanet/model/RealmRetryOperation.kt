package org.ole.planet.myplanet.model

import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import java.util.UUID

open class RealmRetryOperation : RealmObject() {
    @PrimaryKey
    var id: String = ""

    @Index
    var uploadType: String = ""

    @Index
    var itemId: String = ""

    var serializedPayload: String = ""
    var endpoint: String = ""
    var httpMethod: String = "POST"
    var dbId: String? = null

    @Index
    var status: String = STATUS_PENDING

    var attemptCount: Int = 0
    var maxAttempts: Int = DEFAULT_MAX_ATTEMPTS

    var lastAttemptTime: Long = 0
    var nextRetryTime: Long = 0
    var createdTime: Long = 0

    var errorMessage: String? = null
    var httpCode: Int? = null

    var modelClassName: String = ""
    var userId: String? = null

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_IN_PROGRESS = "in_progress"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_ABANDONED = "abandoned"

        private const val DEFAULT_MAX_ATTEMPTS = 5
        private const val BASE_DELAY_MS = 30_000L
        private const val MAX_DELAY_MS = 30 * 60 * 1000L

        @JvmStatic
        fun createFromRetryFailure(
            realm: Realm,
            uploadType: String,
            failure: RetryFailure,
            payload: String,
            endpoint: String,
            httpMethod: String,
            dbId: String?,
            modelClassName: String,
            userId: String?
        ): RealmRetryOperation {
            val operation = realm.createObject(
                RealmRetryOperation::class.java,
                UUID.randomUUID().toString()
            )
            operation.uploadType = uploadType
            operation.itemId = failure.itemId
            operation.serializedPayload = payload
            operation.endpoint = endpoint
            operation.httpMethod = httpMethod
            operation.dbId = dbId
            operation.status = STATUS_PENDING
            operation.attemptCount = 1
            operation.createdTime = System.currentTimeMillis()
            operation.lastAttemptTime = System.currentTimeMillis()
            operation.nextRetryTime = calculateNextRetryTime(1)
            operation.errorMessage = failure.message
            operation.httpCode = failure.httpCode
            operation.modelClassName = modelClassName
            operation.userId = userId
            return operation
        }

        @JvmStatic
        fun calculateNextRetryTime(attemptCount: Int): Long {
            val delay = minOf(BASE_DELAY_MS * (1L shl attemptCount), MAX_DELAY_MS)
            return System.currentTimeMillis() + delay
        }
    }
}
