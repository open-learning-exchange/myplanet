package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.queryList
import org.ole.planet.myplanet.model.RealmRating

class RatingRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService
) : RatingRepository {

    override suspend fun getRatings(type: String, userId: String?): Map<String, Int> {
        return databaseService.withRealmAsync { realm ->
            val ratings = realm.queryList(RealmRating::class.java) {
                equalTo("type", type)
                equalTo("userId", userId)
            }
            ratings.associate { (it.item ?: "") to (it.rate?.toInt() ?: 0) }
        }
    }
}
