package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import java.util.Date
import java.util.UUID
import javax.inject.Inject
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
        userMetadata: RatingUserMetadata?,
        rating: Float,
        comment: String,
    ): RatingSummary {
        val resolvedUser = resolveUserMetadata(userId, userMetadata)
        val resolvedUserId = resolvedUser.resolvedUserId()
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

    private suspend fun resolveUserMetadata(
        userId: String,
        providedMetadata: RatingUserMetadata?,
    ): RatingUserMetadata {
        if (providedMetadata != null) {
            val resolvedId = providedMetadata.resolvedUserId()
            require(!resolvedId.isNullOrBlank()) { "Provided user metadata is missing a valid identifier" }
            return providedMetadata
        }

        require(userId.isNotBlank()) { "User ID is required to submit a rating" }

        val user = findByField(RealmUserModel::class.java, "id", userId)
            ?: findByField(RealmUserModel::class.java, "_id", userId)

        val resolvedUser = requireNotNull(user) { "Unable to locate user with ID '$userId'" }
        return resolvedUser.toRatingUserMetadata()
    }

    private fun setRatingData(
        ratingObject: RealmRating,
        userMetadata: RatingUserMetadata,
        type: String,
        itemId: String,
        title: String,
        rating: Float,
        comment: String,
    ) {
        val resolvedUserId = userMetadata.resolvedUserId()
        require(!resolvedUserId.isNullOrBlank()) { "User data is missing a valid identifier" }

        ratingObject.apply {
            isUpdated = true
            this.comment = comment
            rate = rating.toInt()
            time = Date().time
            userId = resolvedUserId
            createdOn = userMetadata.parentCode
            parentCode = userMetadata.parentCode
            planetCode = userMetadata.planetCode
            user = gson.toJson(userMetadata.serialized)
            this.type = type
            item = itemId
            this.title = title
        }
    }

    private fun RealmUserModel.toRatingUserMetadata(): RatingUserMetadata =
        RatingUserMetadata(
            primaryId = id,
            legacyId = _id,
            parentCode = parentCode,
            planetCode = planetCode,
            serialized = serialize(),
        )
}
