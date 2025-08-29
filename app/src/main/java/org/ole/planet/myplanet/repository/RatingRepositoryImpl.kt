package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import java.util.Date
import java.util.UUID
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
        return ratings.associate { (it.item ?: "") to (it.rate?.toInt() ?: 0) }
    }

    override suspend fun getUserRating(
        type: String?,
        userId: String?,
        itemId: String?
    ): RealmRating? {
        return withRealm { realm ->
            realm.where(RealmRating::class.java)
                .equalTo("type", type)
                .equalTo("userId", userId)
                .equalTo("item", itemId)
                .findFirst()?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun saveRating(
        type: String?,
        itemId: String?,
        title: String?,
        userId: String?,
        comment: String,
        rating: Int
    ) {
        executeTransaction { realm ->
            var ratingObject = realm.where(RealmRating::class.java)
                .equalTo("type", type)
                .equalTo("userId", userId)
                .equalTo("item", itemId)
                .findFirst()
            if (ratingObject == null) {
                ratingObject = realm.createObject(RealmRating::class.java, UUID.randomUUID().toString())
            }
            val model = realm.where(RealmUserModel::class.java)
                .equalTo("id", userId)
                .findFirst()
            ratingObject?.apply {
                isUpdated = true
                this.comment = comment
                rate = rating
                time = Date().time
                this.userId = model?.id
                createdOn = model?.parentCode
                parentCode = model?.parentCode
                planetCode = model?.planetCode
                user = Gson().toJson(model?.serialize())
                this.type = type
                item = itemId
                this.title = title
            }
        }
    }
}
