package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.model.RealmUserModel

class RatingRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    private val gson: Gson,
) : RealmRepository(databaseService), RatingRepository {

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
        userId: String,
        rating: Float,
        comment: String,
    ): RatingSummary {
        val resolvedUser = findUserForRating(userId)
        val resolvedUserId = resolvedUser.id?.takeIf { it.isNotBlank() } ?: resolvedUser._id
        require(!resolvedUserId.isNullOrBlank()) { "Resolved user is missing an identifier" }

        val existingRating = queryList(RealmRating::class.java) {
            equalTo("type", type)
            equalTo("userId", resolvedUserId)
            equalTo("item", itemId)
        }.firstOrNull()

        if (existingRating == null || existingRating.id.isNullOrBlank()) {
            val newRating = RealmRating().apply {
                id = UUID.randomUUID().toString()
            }
            setRatingData(newRating, resolvedUser, type, itemId, title, rating, comment)
            save(newRating)
        } else {
            update(RealmRating::class.java, "id", existingRating.id!!) { ratingObject ->
                setRatingData(ratingObject, resolvedUser, type, itemId, title, rating, comment)
            }
        }

        return getRatingSummary(type, itemId, resolvedUserId)
    }

    private fun RealmRating.toRatingEntry(): RatingEntry =
        RatingEntry(
            id = id,
            comment = comment,
            rate = rate,
        )

    private suspend fun findUserForRating(userId: String): RealmUserModel {
        require(userId.isNotBlank()) { "User ID is required to submit a rating" }

        val user = findByField(RealmUserModel::class.java, "id", userId)
            ?: findByField(RealmUserModel::class.java, "_id", userId)

        return requireNotNull(user) { "Unable to locate user with ID '$userId'" }
    }

    private fun setRatingData(
        ratingObject: RealmRating,
        userModel: RealmUserModel?,
        type: String,
        itemId: String,
        title: String,
        rating: Float,
        comment: String,
    ) {
        val resolvedUser = requireNotNull(userModel) { "User data is required to save a rating" }
        val resolvedUserId =
            resolvedUser.id?.takeIf { it.isNotBlank() } ?: resolvedUser._id
        require(!resolvedUserId.isNullOrBlank()) { "User data is missing a valid identifier" }

        ratingObject.apply {
            isUpdated = true
            this.comment = comment
            rate = roundToSupportedRating(rating)
            time = Date().time
            userId = resolvedUserId
            createdOn = resolvedUser.parentCode
            parentCode = resolvedUser.parentCode
            planetCode = resolvedUser.planetCode
            user = gson.toJson(resolvedUser.serialize())
            this.type = type
            item = itemId
            this.title = title
        }
    }

    companion object {
        private const val MIN_RATING = 1
        private const val MAX_RATING = 5

        internal fun roundToSupportedRating(rating: Float): Int {
            return rating.roundToInt().coerceIn(MIN_RATING, MAX_RATING)
        }
    }
}
