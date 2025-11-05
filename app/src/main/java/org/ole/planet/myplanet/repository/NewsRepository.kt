package org.ole.planet.myplanet.repository

import java.util.HashMap
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel

interface NewsRepository {
    suspend fun getNewsWithReplies(newsId: String): Pair<RealmNews?, List<RealmNews>>
    suspend fun getCommunityVisibleNews(userIdentifier: String): List<RealmNews>
    suspend fun createNews(map: HashMap<String?, String>, user: RealmUserModel?): RealmNews
    suspend fun addLabel(newsId: String, label: String)
    suspend fun removeLabel(newsId: String, label: String)
}
