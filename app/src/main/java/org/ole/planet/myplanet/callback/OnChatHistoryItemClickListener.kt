package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.RealmConversation

interface OnChatHistoryItemClickListener {
    fun onChatHistoryItemClicked(conversations: List<RealmConversation>?, id: String, rev: String?, aiProvider: String?)
}
