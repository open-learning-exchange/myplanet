package org.ole.planet.myplanet.model

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class RealmTeamNotification : RealmObject() {
    @PrimaryKey
    var id: String? = null
    @JvmField
    var type: String? = null
    @JvmField
    var parentId: String? = null
    @JvmField
    var lastCount = 0
}
