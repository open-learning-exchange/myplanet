package org.ole.planet.myplanet.model

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class RealmResourceActivity : RealmObject() {
    @PrimaryKey
    var id: String? = null
    var _id: String? = null
    var createdOn: String? = null
    var _rev: String? = null
    var time: Long = 0
    var title: String? = null
    var resourceId: String? = null
    var parentCode: String? = null
    var type: String? = null
    var user: String? = null
    var androidId: String? = null
}
