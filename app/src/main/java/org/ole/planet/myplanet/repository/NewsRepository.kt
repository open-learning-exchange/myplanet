package org.ole.planet.myplanet.repository

import java.util.HashMap
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.NewsItem
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel

interface NewsRepository {
    suspend fun getCommunityNews(userIdentifier: String): Flow<List<NewsItem>>
    suspend fun getNewsWithReplies(newsId: String): Pair<NewsItem?, List<NewsItem>>
    suspend fun getCommunityVisibleNews(userIdentifier: String): List<NewsItem>
    suspend fun createNews(map: HashMap<String?, String>, user: RealmUserModel?): RealmNews
}
