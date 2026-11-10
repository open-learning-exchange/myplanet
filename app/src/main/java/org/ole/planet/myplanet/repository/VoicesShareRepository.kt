package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.model.UserEntity

interface VoicesShareRepository {
    suspend fun isAlreadyShared(chatId: String, viewInId: String): Boolean
    suspend fun createNews(map: HashMap<String?, String>, user: UserEntity?, imageList: List<String>?): News
    suspend fun getPlanetNewsMessages(planetCode: String?): List<News>
}
