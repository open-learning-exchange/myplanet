package org.ole.planet.myplanet.ui.chat

import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.RealmMyTeam
import java.io.Serializable

data class ChatHistoryData(
    val user: RealmUserModel?,
    val chatHistory: List<RealmChatHistory>,
    val sharedNewsMessages: List<RealmNews>,
    val shareTargets: ChatShareTargets,
) : Serializable

data class ChatShareTargets(
    val community: RealmMyTeam?,
    val teams: List<RealmMyTeam>,
    val enterprises: List<RealmMyTeam>
) : Serializable
