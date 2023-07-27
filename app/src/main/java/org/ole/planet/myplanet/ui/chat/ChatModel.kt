package org.ole.planet.myplanet.ui.chat

import com.google.gson.annotations.SerializedName

data class ChatModel(
    @SerializedName("message") var message: String? = null,
    @SerializedName("error") var error: String? = null,
    @SerializedName("status") var status: String? = null,
    @SerializedName("chat") var chat: String? = null,
    @SerializedName("history") var history: ArrayList<History> = arrayListOf(),
    @SerializedName("couchDBResponse") var couchDBResponse: CouchDBResponse? = CouchDBResponse()
)

data class History(
    @SerializedName("query") var query: String? = null,
    @SerializedName("response") var response: String? = null
)

data class CouchDBResponse(
    @SerializedName("ok") var ok: Boolean? = null,
    @SerializedName("id") var id: String? = null,
    @SerializedName("rev") var rev: String? = null
)