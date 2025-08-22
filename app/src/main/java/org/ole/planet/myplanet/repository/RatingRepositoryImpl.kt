package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmRating

class RatingRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), RatingRepository {

    override suspend fun getRatings(type: String, userId: String?): Map<String, Int> {
        val ratings = queryList(RealmRating::class.java) {
            equalTo("type", type)
            equalTo("userId", userId)
        }
        return ratings.associate { (it.item ?: "") to (it.rate?.toInt() ?: 0) }
    }
}
