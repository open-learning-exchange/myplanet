package org.ole.planet.myplanet.repository

import java.util.HashMap
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel

interface NewsRepository {
    suspend fun getNewsWithReplies(newsId: String): Pair<RealmNews?, List<RealmNews>>
    suspend fun getMessagesForPlanet(planetCode: String?): List<RealmNews>
    suspend fun createChatNews(map: HashMap<String?, String>, user: RealmUserModel?): RealmNews?
}
