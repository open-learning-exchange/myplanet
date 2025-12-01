package org.ole.planet.myplanet.repository

import java.util.HashMap
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel

interface NewsRepository {
    suspend fun getCommunityNews(userIdentifier: String): Flow<List<RealmNews>>
    suspend fun getCommunityNewsItems(userIdentifier: String): List<org.ole.planet.myplanet.model.NewsItem>
    suspend fun getNewsWithReplies(newsId: String): Pair<RealmNews?, List<RealmNews>>
    suspend fun getCommunityVisibleNews(userIdentifier: String): List<RealmNews>
    suspend fun createNews(map: HashMap<String?, String>, user: RealmUserModel?): RealmNews
    suspend fun deleteNews(newsId: String)
    suspend fun addLabel(newsId: String, label: String)
    suspend fun removeLabel(newsId: String, label: String)
    suspend fun getNewsItemWithReplies(newsId: String): Pair<org.ole.planet.myplanet.model.NewsItem?, List<org.ole.planet.myplanet.model.NewsItem>>
    suspend fun getNewsItemsByIds(ids: List<String>): List<org.ole.planet.myplanet.model.NewsItem>
}
