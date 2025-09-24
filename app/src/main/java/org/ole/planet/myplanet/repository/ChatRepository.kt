package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmChatHistory

interface ChatRepository {
    suspend fun getChatHistoryForUser(userName: String?): List<RealmChatHistory>
}
