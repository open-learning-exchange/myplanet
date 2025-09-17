package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmRating

class RatingRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), RatingRepository {

    override suspend fun getRatings(type: String, userId: String?): Map<String?, JsonObject> {
        return withRealm { realm ->
            val results = realm.where(RealmRating::class.java)
                .equalTo("type", type)
                .findAll()

            val ratings = HashMap<String?, JsonObject>()
            results.forEach { rating ->
                val ratingObject = RealmRating.getRatingsById(realm, rating.type, rating.item, userId)
                if (ratingObject != null) {
                    ratings[rating.item] = ratingObject
                }
            }
            ratings
        }
    }
}
