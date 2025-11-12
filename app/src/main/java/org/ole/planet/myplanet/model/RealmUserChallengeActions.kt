package org.ole.planet.myplanet.model

import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import java.util.UUID

open class RealmUserChallengeActions : RealmObject() {
    @PrimaryKey
    var id: String = UUID.randomUUID().toString()
    var userId: String? = null
    var actionType: String? = null
    var resourceId: String? = null
    var time: Long = 0

    companion object {
        fun createActionAsync(
            userId: String,
            resourceId: String?,
            actionType: String
        ) {
            val realm = Realm.getDefaultInstance()
            realm.executeTransactionAsync({ bgRealm ->
                val action = bgRealm.createObject(
                    RealmUserChallengeActions::class.java,
                    UUID.randomUUID().toString()
                )
                action.userId     = userId
                action.actionType = actionType
                action.resourceId = resourceId
                action.time       = System.currentTimeMillis()
            }, {
                realm.close()
            }, { e ->
                e.printStackTrace()
                realm.close()
            })
        }

        @Deprecated("Use createActionAsync without realm parameter")
        fun createActionAsync(
            realm: Realm,
            userId: String,
            resourceId: String?,
            actionType: String
        ) {
            createActionAsync(userId, resourceId, actionType)
        }
    }
}
