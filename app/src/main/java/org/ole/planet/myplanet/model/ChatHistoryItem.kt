package org.ole.planet.myplanet.model

data class ChatHistoryItem(
    val _id: String,
    val _rev: String?,
    val title: String?,
    val aiProvider: String?,
    val conversations: List<RealmConversation>?
)
