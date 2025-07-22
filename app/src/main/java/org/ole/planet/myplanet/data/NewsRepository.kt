package org.ole.planet.myplanet.data

import io.realm.RealmList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel

interface NewsRepository {
    suspend fun createNews(
        map: HashMap<String?, String>,
        user: RealmUserModel?,
        imageUrls: RealmList<String>?,
        isReply: Boolean = false
    ): RealmNews
}

@Singleton
class NewsRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService
) : NewsRepository {
    override suspend fun createNews(
        map: HashMap<String?, String>,
        user: RealmUserModel?,
        imageUrls: RealmList<String>?,
        isReply: Boolean
    ): RealmNews = withContext(Dispatchers.IO) {
        val realm = databaseService.realmInstance
        try {
            val news = RealmNews.createNews(map, realm, user, imageUrls, isReply)
            realm.copyFromRealm(news)
        } finally {
            realm.close()
        }
    }
}
