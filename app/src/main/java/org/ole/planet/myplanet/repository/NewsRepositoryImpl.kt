package org.ole.planet.myplanet.repository

import io.realm.Case
import io.realm.Sort
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmNews

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
}
