package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.ChatMessage

interface OnChatItemClickListener {
    fun onChatItemClick(position: Int, chatItem: ChatMessage)
}
