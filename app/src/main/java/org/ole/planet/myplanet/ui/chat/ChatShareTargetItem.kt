package org.ole.planet.myplanet.ui.chat

data class ChatShareTargetItem(
    val title: String,
    val isGroup: Boolean,
    val isExpanded: Boolean = false,
    val parentTitle: String? = null,
    val isShared: Boolean = false
)
