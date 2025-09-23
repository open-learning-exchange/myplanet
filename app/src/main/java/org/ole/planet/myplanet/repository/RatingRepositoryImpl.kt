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

    private val gson = Gson()

    override suspend fun getRatings(type: String, userId: String?): Map<String, Int> {
        val ratings = queryList(RealmRating::class.java) {
            equalTo("type", type)
            equalTo("userId", userId)
        }
        return ratings.associate { (it.item ?: "") to it.rate }
    }

    override suspend fun getRatingSummary(
        type: String,
        itemId: String,
        userId: String,
    ): RatingSummary {
        val ratings = queryList(RealmRating::class.java) {
            equalTo("type", type)
            equalTo("item", itemId)
        }
        val existingRating = ratings.firstOrNull { it.userId == userId }

        val totalRatings = ratings.size
        val averageRating = if (totalRatings > 0) {
            ratings.sumOf { it.rate }.toFloat() / totalRatings
        } else {
            0f
        }

        return RatingSummary(
            existingRating = existingRating?.toRatingEntry(),
            averageRating = averageRating,
            totalRatings = totalRatings,
            userRating = existingRating?.rate,
        )
    }

    override suspend fun submitRating(
        type: String,
        itemId: String,
        title: String,
        user: RealmUserModel,
        rating: Float,
        comment: String,
    ): RatingSummary {
        val userId = user.id ?: user._id
        require(!userId.isNullOrBlank()) { "User ID is required to submit a rating" }

        val existingRating = queryList(RealmRating::class.java) {
            equalTo("type", type)
            equalTo("userId", userId)
            equalTo("item", itemId)
        }.firstOrNull()

        if (existingRating == null || existingRating.id.isNullOrBlank()) {
            val newRating = RealmRating().apply {
                id = UUID.randomUUID().toString()
            }
            setRatingData(newRating, user, type, itemId, title, rating, comment)
            save(newRating)
        } else {
            update(RealmRating::class.java, "id", existingRating.id!!) { ratingObject ->
                setRatingData(ratingObject, user, type, itemId, title, rating, comment)
            }
        }

        return getRatingSummary(type, itemId, userId)
    }

    private fun RealmRating.toRatingEntry(): RatingEntry =
        RatingEntry(
            id = id,
            comment = comment,
            rate = rate,
        )

    private fun setRatingData(
        ratingObject: RealmRating,
        userModel: RealmUserModel,
        type: String,
        itemId: String,
        title: String,
        rating: Float,
        comment: String,
    ) {
        ratingObject.apply {
            isUpdated = true
            this.comment = comment
            rate = rating.toInt()
            time = Date().time
            userId = userModel.id ?: userModel._id
            createdOn = userModel.parentCode
            parentCode = userModel.parentCode
            planetCode = userModel.planetCode
            user = gson.toJson(userModel.serialize())
            this.type = type
            item = itemId
            this.title = title
        }
    }

}
