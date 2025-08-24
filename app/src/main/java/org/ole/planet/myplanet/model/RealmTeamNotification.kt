package org.ole.planet.myplanet.model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class RealmTeamNotification : RealmObject {
    @PrimaryKey
    var id: String? = null
    var type: String? = null
    var parentId: String? = null
    var lastCount = 0
}
