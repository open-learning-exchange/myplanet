package org.ole.planet.myplanet.model

import io.realm.kotlin.Realm
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import io.realm.kotlin.types.annotations.PrimaryKey

class RealmUserChallengeActions : RealmObject {
    @PrimaryKey
    var id: String = RealmUUID.random().toString()
    var userId: String? = null
    var actionType: String? = null
    var resourceId: String? = null
    var time: Long = 0

    companion object {
        suspend fun createAction(realm: Realm, userId: String, resourceId: String?, actionType: String) {
            realm.write {
                copyToRealm(RealmUserChallengeActions().apply {
                    this.id = RealmUUID.random().toString()
                    this.userId = userId
                    this.actionType = actionType
                    this.resourceId = resourceId
                    this.time = System.currentTimeMillis()
                })
            }
        }
    }
}
