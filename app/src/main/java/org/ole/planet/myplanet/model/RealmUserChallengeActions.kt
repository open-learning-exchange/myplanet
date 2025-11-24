package org.ole.planet.myplanet.model

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.datamanager.DatabaseService
import java.util.UUID

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
