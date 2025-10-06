package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmNews

interface NewsRepository {
    suspend fun getNewsWithReplies(newsId: String): Pair<RealmNews?, List<RealmNews>>
    suspend fun getCommunityVisibleNews(userIdentifier: String): List<RealmNews>
}
