package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.model.RealmUserModel

class RatingRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), RatingRepository {

    override suspend fun getRatings(type: String, userId: String?): Map<String, Int> {
        val ratings = queryList(RealmRating::class.java) {
            equalTo("type", type)
            equalTo("userId", userId)
        }
        return ratings.associate { (it.item ?: "") to it.rate }
    }

    override suspend fun saveRating(rating: RealmRating) {
        save(rating)
    }

    override suspend fun getRating(type: String, itemId: String, userId: String): RealmRating? {
        return queryList(RealmRating::class.java) {
            equalTo("type", type)
            equalTo("item", itemId)
            equalTo("userId", userId)
        }.firstOrNull()
    }

    override suspend fun getUserModel(userId: String): RealmUserModel? {
        return findByField(RealmUserModel::class.java, "id", userId)
    }
}
