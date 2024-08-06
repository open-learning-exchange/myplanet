package org.ole.planet.myplanet.model

import com.google.gson.annotations.SerializedName

data class ChatRequestModel(
    @SerializedName("data") val data: ContentData,
    @SerializedName("save") val save: Boolean
)

data class ContentData(
    @SerializedName("user") var user: String,
    @SerializedName("content") val content: String,
    @SerializedName("aiProvider") val aiProvider: AiProvider
)

data class ContinueChatModel(
    @SerializedName("data") val data: Data,
    @SerializedName("save") val save: Boolean
)

data class Data(
    @SerializedName("user") var user: String,
    @SerializedName("content") var content: String,
    @SerializedName("aiProvider") val aiProvider: AiProvider,
    @SerializedName("_id") var id: String,
    @SerializedName("_rev") var rev: String
)

data class AiProvider (
    @SerializedName("name") val name: String,
    @SerializedName("model") val model: String
)