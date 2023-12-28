package org.ole.planet.myplanet.model

import io.realm.RealmObject

open class Conversation : RealmObject() {
    var query: String? = null
    var response: String? = null
}
