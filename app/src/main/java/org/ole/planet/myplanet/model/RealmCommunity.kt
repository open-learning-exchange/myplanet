package org.ole.planet.myplanet.model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class RealmCommunity : RealmObject {
    @PrimaryKey
    var id: String = ""
    var weight: Int = 10
    var registrationRequest: String = ""
    var localDomain: String = ""
    var name: String = ""
    var parentDomain: String = ""

    override fun toString(): String = name
}