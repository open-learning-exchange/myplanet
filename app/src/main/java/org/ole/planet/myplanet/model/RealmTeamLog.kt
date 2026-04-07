package org.ole.planet.myplanet.model

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class RealmTeamLog : RealmObject() {
    @PrimaryKey
    var id: String? = null
    var _id: String? = null
    var _rev: String? = null
    var teamId: String? = null
    var user: String? = null
    var type: String? = null
    var teamType: String? = null
    var createdOn: String? = null
    var parentCode: String? = null
    var time: Long? = null
    var uploaded = false
}
