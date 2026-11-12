package org.ole.planet.myplanet.model

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatResponse(
    @SerialName("message") @SerializedName("message") var message: String? = null,
    @SerialName("error") @SerializedName("error") var error: String? = null,
    @SerialName("status") @SerializedName("status") var status: String? = null,
    @SerialName("chat") @SerializedName("chat") var chat: String? = null,
    @SerialName("couchDBResponse") @SerializedName("couchDBResponse") var couchDBResponse: CouchDBResponse? = CouchDBResponse()
)

@Serializable
data class CouchDBResponse(
    @SerialName("ok") @SerializedName("ok") var ok: Boolean? = null,
    @SerialName("id") @SerializedName("id") var id: String? = null,
    @SerialName("rev") @SerializedName("rev") var rev: String? = null
)
