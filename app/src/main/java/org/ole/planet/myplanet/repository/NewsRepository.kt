package org.ole.planet.myplanet.repository

import java.util.HashMap
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.news.NewsItem

interface NewsRepository {
    suspend fun getCommunityNews(userIdentifier: String): Flow<List<RealmNews>>
    suspend fun getCommunityNewsItems(userIdentifier: String, teamId: String?, userId: String?): Flow<List<NewsItem>>
    suspend fun getNewsWithReplies(newsId: String): Pair<RealmNews?, List<RealmNews>>
    suspend fun getCommunityVisibleNews(userIdentifier: String): List<RealmNews>
    suspend fun createNews(map: HashMap<String?, String>, user: RealmUserModel?): RealmNews
}
