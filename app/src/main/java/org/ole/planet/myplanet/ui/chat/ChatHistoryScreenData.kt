package org.ole.planet.myplanet.ui.chat

import org.ole.planet.myplanet.model.ChatShareTargets
import org.ole.planet.myplanet.model.ChatHistory
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUser

data class ChatHistoryScreenData(
    val currentUser: RealmUser?,
    val chatHistory: List<ChatHistory>,
    val newsMessages: List<RealmNews>,
    val shareTargets: ChatShareTargets
)
