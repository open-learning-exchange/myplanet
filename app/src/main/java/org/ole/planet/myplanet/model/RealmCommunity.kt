package org.ole.planet.myplanet.model

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class RealmCommunity : RealmObject() {
    @PrimaryKey
    var id: String = ""
    var weight: Int = 10
    var registrationRequest: String = ""
    var localDomain: String = ""
    var name: String = ""
    var parentDomain: String = ""


    override fun toString(): String {
        return name
    }
}