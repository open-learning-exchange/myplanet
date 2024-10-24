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
    }
}