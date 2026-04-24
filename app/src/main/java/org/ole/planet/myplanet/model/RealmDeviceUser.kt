package org.ole.planet.myplanet.model

import io.realm.RealmObject
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey

open class RealmDeviceUser : RealmObject() {
    @PrimaryKey
    var userId: String? = null

    @Index
    var userName: String? = null

    var parentCode: String? = null
    var planetCode: String? = null
    var firstLoginAt: Long = 0
    var lastLoginAt: Long = 0
}
