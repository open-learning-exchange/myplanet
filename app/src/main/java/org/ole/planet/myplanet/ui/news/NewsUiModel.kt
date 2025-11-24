package org.ole.planet.myplanet.ui.news

import org.ole.planet.myplanet.model.Conversation
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.utilities.GsonUtils

data class NewsUiModel(
    val news: RealmNews,
    val conversations: List<Conversation> = emptyList()
)

fun RealmNews.toUiModel(): NewsUiModel {
    val conversations = if (conversations.isNullOrEmpty()) {
        emptyList()
    } else {
        try {
            GsonUtils.gson.fromJson(conversations, Array<Conversation>::class.java).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    return NewsUiModel(this, conversations)
}
