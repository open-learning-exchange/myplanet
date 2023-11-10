package org.ole.planet.myplanet.ui.chat

import io.realm.RealmList
import org.ole.planet.myplanet.model.Conversation

interface ChatHistoryItemClickListener {
    fun onChatHistoryItemClicked(conversations:RealmList<Conversation>, _id: String, _rev: String)
}