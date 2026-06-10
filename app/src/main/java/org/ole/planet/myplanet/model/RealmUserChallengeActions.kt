package org.ole.planet.myplanet.model

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
}
