package org.ole.planet.myplanet.ui.chat

import com.google.gson.annotations.SerializedName

data class ChatRequestModel(
    @SerializedName("content") val content: String
)