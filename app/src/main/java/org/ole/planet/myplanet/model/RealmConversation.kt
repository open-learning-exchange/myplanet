package org.ole.planet.myplanet.model

import io.realm.RealmObject

open class RealmConversation : RealmObject() {
    var query: String? = null
    var response: String? = null
}
