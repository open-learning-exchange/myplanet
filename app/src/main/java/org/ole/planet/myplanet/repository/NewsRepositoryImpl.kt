package org.ole.planet.myplanet.repository

import io.realm.Case
import io.realm.Sort
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import java.util.HashMap
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNews.Companion.createNews
import org.ole.planet.myplanet.model.RealmUserModel

class NewsRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
) : RealmRepository(databaseService), NewsRepository {

    override suspend fun getNewsWithReplies(newsId: String): Pair<RealmNews?, List<RealmNews>> {
        val news = findByField(RealmNews::class.java, "id", newsId)
        val replies = queryList(RealmNews::class.java) {
            equalTo("replyTo", newsId, Case.INSENSITIVE)
            sort("time", Sort.DESCENDING)
        }
        return news to replies
    }

    override suspend fun getMessagesForPlanet(planetCode: String?): List<RealmNews> {
        if (planetCode.isNullOrBlank()) {
            return emptyList()
        }
        return queryList(RealmNews::class.java) {
            equalTo("docType", "message", Case.INSENSITIVE)
            equalTo("createdOn", planetCode, Case.INSENSITIVE)
        }
    }

    override suspend fun createChatNews(
        map: HashMap<String?, String>,
        user: RealmUserModel?,
    ): RealmNews? {
        user ?: return null
        return withRealmAsync { realm ->
            val news = createNews(map, realm, user, null)
            realm.copyFromRealm(news)
        }
    }
}
