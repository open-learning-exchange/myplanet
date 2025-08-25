package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.model.RealmUserModel

interface RatingRepository {
    suspend fun getRatings(type: String, userId: String?): Map<String, Int>
    suspend fun saveRating(rating: RealmRating)
    suspend fun getRating(type: String, itemId: String, userId: String): RealmRating?
    suspend fun getUserModel(userId: String): RealmUserModel?
}
