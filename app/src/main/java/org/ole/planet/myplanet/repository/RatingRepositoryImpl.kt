package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.model.RealmUserModel

class RatingRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    private val gson: Gson,
) : RealmRepository(databaseService), RatingRepository {

    private val userCache = ConcurrentHashMap<String, RatingUserSnapshot>()

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
        val resolvedUser = resolveUserForRating(userId)
        val resolvedUserId = resolvedUser.canonicalId

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

    private suspend fun resolveUserForRating(userId: String): RatingUserSnapshot {
        require(userId.isNotBlank()) { "User ID is required to submit a rating" }

        userCache[userId]?.let { return it }

        val user = findByField(RealmUserModel::class.java, "id", userId)
            ?: findByField(RealmUserModel::class.java, "_id", userId)

        val resolvedUser = requireNotNull(user) { "Unable to locate user with ID '$userId'" }
        val snapshot = createUserSnapshot(resolvedUser)
        snapshot.identifiers.forEach { identifier ->
            userCache[identifier] = snapshot
        }
        return snapshot
    }

    private fun setRatingData(
        ratingObject: RealmRating,
        userSnapshot: RatingUserSnapshot,
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
            userId = userSnapshot.canonicalId
            createdOn = userSnapshot.parentCode
            parentCode = userSnapshot.parentCode
            planetCode = userSnapshot.planetCode
            user = userSnapshot.serializedUser
            this.type = type
            item = itemId
            this.title = title
        }
    }

    private fun createUserSnapshot(userModel: RealmUserModel): RatingUserSnapshot {
        val canonicalId = userModel.id?.takeIf { it.isNotBlank() } ?: userModel._id
        require(!canonicalId.isNullOrBlank()) { "User data is missing a valid identifier" }

        return RatingUserSnapshot(
            canonicalId = canonicalId,
            userId = userModel.id,
            realmId = userModel._id,
            parentCode = userModel.parentCode,
            planetCode = userModel.planetCode,
            serializedUser = gson.toJson(userModel.serialize()),
        )
    }

    private data class RatingUserSnapshot(
        val canonicalId: String,
        val userId: String?,
        val realmId: String?,
        val parentCode: String?,
        val planetCode: String?,
        val serializedUser: String,
    ) {
        val identifiers: Set<String> = listOfNotNull(canonicalId, userId, realmId).toSet()
    }
}
