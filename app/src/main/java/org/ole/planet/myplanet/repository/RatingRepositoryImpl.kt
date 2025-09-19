package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.model.RealmRating.Companion.getRatingsById

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

    override suspend fun getRatingSummary(type: String, itemId: String?, userId: String?): JsonObject? {
        if (itemId.isNullOrEmpty()) {
            return null
        }
        return withRealm { realm ->
            getRatingsById(realm, type, itemId, userId)
        }
    }
}
