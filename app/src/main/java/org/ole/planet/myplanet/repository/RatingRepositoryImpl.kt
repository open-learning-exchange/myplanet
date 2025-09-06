package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.model.RealmUserModel

class RatingRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService,
    private val gson: Gson
) : RealmRepository(databaseService), RatingRepository {

    override suspend fun getRatings(type: String, userId: String?): Map<String, Int> {
        val ratings = queryList(RealmRating::class.java) {
            equalTo("type", type)
            equalTo("userId", userId)
        }
        return ratings.associate { (it.item ?: "") to (it.rate) }
    }

    override suspend fun getRating(
        type: String,
        itemId: String,
        userId: String?
    ): RealmRating? {
        return queryList(RealmRating::class.java) {
            equalTo("type", type)
            equalTo("item", itemId)
            equalTo("userId", userId)
        }.firstOrNull()
    }

    override suspend fun getRatingSummary(type: String, itemId: String): Pair<Float, Int> {
        val ratings = queryList(RealmRating::class.java) {
            equalTo("type", type)
            equalTo("item", itemId)
        }
        val total = ratings.size
        val average = if (total > 0) {
            ratings.sumOf { it.rate }.toFloat() / total
        } else {
            0f
        }
        return Pair(average, total)
    }

    override suspend fun submitRating(
        type: String,
        itemId: String,
        title: String,
        userId: String,
        rating: Float,
        comment: String
    ) {
        databaseService.executeTransactionAsync { realm ->
            var ratingObject = realm.where(RealmRating::class.java)
                .equalTo("type", type)
                .equalTo("userId", userId)
                .equalTo("item", itemId)
                .findFirst()

            if (ratingObject == null) {
                ratingObject = realm.createObject(
                    RealmRating::class.java,
                    UUID.randomUUID().toString()
                )
            }

            val userModelCopy = realm.where(RealmUserModel::class.java)
                .equalTo("id", userId)
                .findFirst()

            ratingObject?.apply {
                isUpdated = true
                this.comment = comment
                rate = rating.toInt()
                time = Date().time
                this.userId = userModelCopy?.id
                createdOn = userModelCopy?.parentCode
                parentCode = userModelCopy?.parentCode
                planetCode = userModelCopy?.planetCode
                user = gson.toJson(userModelCopy?.serialize())
                this.type = type
                item = itemId
                this.title = title
            }
        }
    }
}
