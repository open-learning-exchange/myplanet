package org.ole.planet.myplanet.model

import io.realm.kotlin.types.RealmObject

class Conversation : RealmObject {
    var query: String? = null
    var response: String? = null
}