package org.ole.planet.myplanet.ui.chat

import com.google.gson.annotations.SerializedName

data class ChatRequestModel(
    @SerializedName("data")
    val data: ContentData,
    @SerializedName("save")
    val save: Boolean
)

data class ContentData(
    @SerializedName("content")
    val content: String
)
