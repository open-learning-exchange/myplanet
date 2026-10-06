package org.ole.planet.myplanet.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatResponse(
    @SerialName("message") var message: String? = null,
    @SerialName("error") var error: String? = null,
    @SerialName("status") var status: String? = null,
    @SerialName("chat") var chat: String? = null,
    @SerialName("couchDBResponse") var couchDBResponse: CouchDBResponse? = CouchDBResponse()
)

@Serializable
data class CouchDBResponse(
    @SerialName("ok") var ok: Boolean? = null,
    @SerialName("id") var id: String? = null,
    @SerialName("rev") var rev: String? = null
)
