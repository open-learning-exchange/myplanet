package org.ole.planet.myplanet.model

import io.realm.kotlin.Realm
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.UUID

class RealmUserChallengeActions : RealmObject {
    @PrimaryKey
    var id: String = UUID.randomUUID().toString()
    var userId: String? = null
    var actionType: String? = null
    var resourceId: String? = null
    var time: Long = 0

    companion object {
        fun createAction(realm: Realm, userId: String, resourceId: String?, actionType: String) {
            realm.executeTransaction { transactionRealm ->
                val action = transactionRealm.createObject(
                    RealmUserChallengeActions::class.java, UUID.randomUUID().toString()
                )
                action.userId = userId
                action.actionType = actionType
                action.resourceId = resourceId
                action.time = System.currentTimeMillis()
            }
        }

        fun createActionAsync(
            realm: Realm,
            userId: String,
            resourceId: String?,
            actionType: String
        ) {
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
            }, { e ->
                e.printStackTrace()
            })
        }
    }
}
