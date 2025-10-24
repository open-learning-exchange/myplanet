package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.model.RealmNews

interface ChatRepository {
    suspend fun getChatHistoryForUser(userName: String?): List<RealmChatHistory>
    suspend fun getPlanetNewsMessages(planetCode: String?): List<RealmNews>
    suspend fun appendConversationToChatHistory(
        chatHistoryId: String?,
        query: String?,
        response: String?,
        newRev: String?,
    )

    suspend fun getLatestRevisionId(chatHistoryId: String): String?
}
