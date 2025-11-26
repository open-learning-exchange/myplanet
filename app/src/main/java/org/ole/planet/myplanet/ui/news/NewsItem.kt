package org.ole.planet.myplanet.ui.news

import org.ole.planet.myplanet.model.RealmNews

data class NewsItem(
    val news: RealmNews?,
    val replyCount: Int,
    val isTeamLeader: Boolean
)
