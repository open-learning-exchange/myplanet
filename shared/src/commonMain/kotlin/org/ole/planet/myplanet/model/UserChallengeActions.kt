package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.uuid.Uuid

@Entity(tableName = "user_challenge_actions", indices = [Index(value = ["userId", "actionType"])])
open class UserChallengeActions {
    @PrimaryKey
    var id: String = Uuid.random().toString()
    var userId: String? = null
    var actionType: String? = null
    var resourceId: String? = null
    var time: Long = 0
}
