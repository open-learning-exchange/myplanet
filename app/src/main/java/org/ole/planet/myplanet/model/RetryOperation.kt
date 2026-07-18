package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Room replacement for the former Realm `RetryOperation` model. Persistence goes through
 * [org.ole.planet.myplanet.data.room.dao.RetryDao]; the class name is kept because the retry
 * worker/queue and settings screen use it as a plain data holder.
 */
@Entity(
    tableName = "retry_operation",
    indices = [Index("uploadType"), Index("itemId"), Index("status")]
)
open class RetryOperation {
    @PrimaryKey
    var id: String = ""

    var uploadType: String = ""

    var itemId: String = ""

    var serializedPayload: String = ""
    var endpoint: String = ""
    var httpMethod: String = "POST"
    var dbId: String? = null

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

        fun createFromRetryFailure(
            uploadType: String,
            failure: RetryFailure,
            payload: String,
            endpoint: String,
            httpMethod: String,
            dbId: String?,
            modelClassName: String,
            userId: String?
        ): RetryOperation {
            return RetryOperation().apply {
                id = UUID.randomUUID().toString()
                this.uploadType = uploadType
                itemId = failure.itemId
                serializedPayload = payload
                this.endpoint = endpoint
                this.httpMethod = httpMethod
                this.dbId = dbId
                status = STATUS_PENDING
                attemptCount = 1
                createdTime = System.currentTimeMillis()
                lastAttemptTime = System.currentTimeMillis()
                nextRetryTime = calculateNextRetryTime(1)
                errorMessage = failure.message
                httpCode = failure.httpCode
                this.modelClassName = modelClassName
                this.userId = userId
            }
        }

        fun calculateNextRetryTime(attemptCount: Int): Long {
            val delay = minOf(BASE_DELAY_MS * (1L shl attemptCount), MAX_DELAY_MS)
            return System.currentTimeMillis() + delay
        }
    }
}
