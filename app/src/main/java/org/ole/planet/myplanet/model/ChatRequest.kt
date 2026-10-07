package org.ole.planet.myplanet.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    @SerialName("data") val data: ContentData,
    @SerialName("save") val save: Boolean
)

@Serializable
data class ContentData(
    @SerialName("user") var user: String,
    @SerialName("content") val content: String,
    @SerialName("aiProvider") val aiProvider: AiProvider
)

@Serializable
data class ContinueChatRequest(
    @SerialName("data") val data: Data,
    @SerialName("save") val save: Boolean
)

@Serializable
data class Data(
    @SerialName("user") var user: String,
    @SerialName("content") var content: String,
    @SerialName("aiProvider") val aiProvider: AiProvider,
    @SerialName("_id") var id: String,
    @SerialName("_rev") var rev: String
)

@Serializable
data class AiProvider (
    @SerialName("name") val name: String,
    @SerialName("model") val model: String
)
