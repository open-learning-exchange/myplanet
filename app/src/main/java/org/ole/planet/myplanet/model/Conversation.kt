package org.ole.planet.myplanet.model

import kotlinx.serialization.Serializable

@Serializable
open class Conversation {
    var query: String? = null
    var response: String? = null
}
