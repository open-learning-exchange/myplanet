package org.ole.planet.myplanet.model

/**
 * Former Realm `RealmConversation`, now a plain data holder. It has no independent identity and is
 * never queried on its own, so it is embedded inside [ChatHistory.conversations] as a JSON
 * list (see the Room type converter) rather than being its own table.
 */
open class RealmConversation {
    var query: String? = null
    var response: String? = null
}
