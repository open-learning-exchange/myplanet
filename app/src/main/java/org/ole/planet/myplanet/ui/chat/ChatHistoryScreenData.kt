package org.ole.planet.myplanet.ui.chat

import org.ole.planet.myplanet.model.ChatShareTargets
import org.ole.planet.myplanet.model.ChatHistory
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.model.UserEntity

data class ChatHistoryScreenData(
    val currentUser: UserEntity?,
    val chatHistory: List<ChatHistory>,
    val newsMessages: List<News>,
    val shareTargets: ChatShareTargets
)
