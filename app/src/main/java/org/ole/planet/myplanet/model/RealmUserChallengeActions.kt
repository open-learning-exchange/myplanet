package org.ole.planet.myplanet.model

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import java.util.UUID
import org.ole.planet.myplanet.data.DatabaseService

open class RealmUserChallengeActions : RealmObject() {
    @PrimaryKey
    var id: String = UUID.randomUUID().toString()
    var userId: String? = null
    var actionType: String? = null
    var resourceId: String? = null
    var time: Long = 0

    companion object {
        suspend fun createActionAsync(
            databaseService: DatabaseService,
            userId: String,
            resourceId: String?,
            actionType: String
        ) {
            databaseService.executeTransactionAsync { bgRealm ->
                val action = bgRealm.createObject(
                    RealmUserChallengeActions::class.java,
                    UUID.randomUUID().toString()
                )
                action.userId = userId
                action.actionType = actionType
                action.resourceId = resourceId
                action.time = System.currentTimeMillis()
            }
        }
    }
}
