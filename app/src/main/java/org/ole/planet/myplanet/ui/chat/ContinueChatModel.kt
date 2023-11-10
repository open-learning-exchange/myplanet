package org.ole.planet.myplanet.ui.chat

import com.google.gson.annotations.SerializedName

data class ContinueChatModel(
    @SerializedName("data") val data: Data,
    @SerializedName("save") val save: Boolean
)

data class Data(
    @SerializedName("content") var content: String,
    @SerializedName("_id") var Id: String,
    @SerializedName("_rev") var rev: String,
)