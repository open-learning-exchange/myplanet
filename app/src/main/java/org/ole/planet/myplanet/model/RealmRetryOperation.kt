package org.ole.planet.myplanet.model

import io.realm.RealmObject
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey

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

        const val DEFAULT_MAX_ATTEMPTS = 5
    }
}
