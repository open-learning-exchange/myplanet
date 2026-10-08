package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.Conversation

interface OnChatHistoryItemClickListener {
    fun onChatHistoryItemClicked(conversations: List<Conversation>?, id: String, rev: String?, aiProvider: String?)
}
