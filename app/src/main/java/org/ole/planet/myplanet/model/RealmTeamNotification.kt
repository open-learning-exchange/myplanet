package org.ole.planet.myplanet.model

import io.realm.RealmObject
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey

open class RealmTeamNotification : RealmObject() {
    @PrimaryKey
    var id: String? = null
    @Index
    var type: String? = null
    var parentId: String? = null
    var lastCount = 0
}
