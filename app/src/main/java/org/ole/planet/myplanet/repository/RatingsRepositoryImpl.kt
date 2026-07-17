package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.room.dao.RatingDao
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.utils.JsonUtils

class RatingsRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @RealmDispatcher realmDispatcher: CoroutineDispatcher,
    private val gson: Gson,
    private val ratingDao: RatingDao,
) : RealmRepository(databaseService, realmDispatcher), RatingsRepository {
    // Still extends RealmRepository for RealmUser lookups (RealmUser is not migrated yet);
    // RealmRating itself is now stored in Room via ratingDao.

    override suspend fun getRatings(type: String?, userId: String?): HashMap<String?, JsonObject> {
        val ratings = ratingDao.getByType(type)
        val aggregated = aggregateRatings(ratings, userId)
        val map = HashMap<String?, JsonObject>(Math.ceil(aggregated.size / 0.75).toInt())
        for ((item, aggregation) in aggregated) {
            map[item] = aggregation.toJson()
        }
        return map
    }

    override suspend fun getRatingsById(type: String, resourceId: String?, userId: String?): JsonObject? {
        val ratings = ratingDao.getByTypeAndItem(type, resourceId)
        val aggregated = aggregateRatings(ratings, userId)[resourceId]
        return aggregated?.toJson()
    }

    override suspend fun getCourseRatings(userId: String?): HashMap<String?, JsonObject> {
        return getRatings("course", userId)
    }

    override suspend fun getResourceRatings(userId: String?): HashMap<String?, JsonObject> {
        return getRatings("resource", userId)
    }

    override suspend fun getRatingSummary(
        type: String,
        itemId: String,
        userId: String,
    ): RatingSummary {
        val results = ratingDao.getByTypeAndItem(type, itemId)
        val totalRatings = results.size
        val averageRating = if (totalRatings > 0) {
            results.map { it.rate }.average().toFloat()
        } else {
            0f
        }
        val existingRating = results.firstOrNull { it.userId == userId }
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

        val existingRating = ratingDao.findByTypeUserItem(type, resolvedUserId, itemId)

        if (existingRating == null || existingRating.id.isBlank()) {
            val newRating = RealmRating().apply {
                id = UUID.randomUUID().toString()
            }
            setRatingData(newRating, resolvedUser, type, itemId, title, rating, comment)
            ratingDao.upsert(newRating)
        } else {
            val ratingObject = ratingDao.findById(existingRating.id)
            if (ratingObject != null) {
                setRatingData(ratingObject, resolvedUser, type, itemId, title, rating, comment)
                ratingDao.update(ratingObject)
            }
        }

        return getRatingSummary(type, itemId, resolvedUserId)
    }

    override suspend fun insertRatingsFromSync(documentList: List<JsonObject>) {
        if (documentList.isEmpty()) return
        val entities = documentList.map { act ->
            RealmRating().apply {
                _rev = JsonUtils.getString("_rev", act)
                _id = JsonUtils.getString("_id", act)
                id = JsonUtils.getString("_id", act)
                time = JsonUtils.getLong("time", act)
                title = JsonUtils.getString("title", act)
                type = JsonUtils.getString("type", act)
                item = JsonUtils.getString("item", act)
                rate = JsonUtils.getInt("rate", act)
                isUpdated = false
                comment = JsonUtils.getString("comment", act)
                user = JsonUtils.gson.toJson(JsonUtils.getJsonObject("user", act))
                userId = JsonUtils.getString("_id", JsonUtils.getJsonObject("user", act))
                parentCode = JsonUtils.getString("parentCode", act)
                planetCode = JsonUtils.getString("planetCode", act)
                createdOn = JsonUtils.getString("createdOn", act)
            }
        }
        ratingDao.upsertAll(entities)
    }

    override suspend fun getPendingRatingUploads(): List<RealmRating> {
        return ratingDao.getPendingUploads()
    }

    override suspend fun markRatingUploaded(id: String): Boolean {
        return ratingDao.markUploaded(id) > 0
    }

    private fun RealmRating.toRatingEntry(): RatingEntry =
        RatingEntry(
            id = id,
            comment = comment,
            rate = rate,
        )

    private suspend fun findUserForRating(userId: String): RealmUser {
        require(userId.isNotBlank()) { "User ID is required to submit a rating" }

        val user = findByField(RealmUser::class.java, "id", userId)
            ?: findByField(RealmUser::class.java, "_id", userId)

        return requireNotNull(user) { "Unable to locate user with ID '$userId'" }
    }

    private fun setRatingData(
        ratingObject: RealmRating,
        userModel: RealmUser?,
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

    private fun aggregateRatings(
        ratings: Iterable<RealmRating>,
        userId: String?
    ): Map<String?, RatingAggregation> {
        val aggregationMap = LinkedHashMap<String?, RatingAggregation>()
        for (rating in ratings) {
            val item = rating.item
            val aggregation = aggregationMap.getOrPut(item) { RatingAggregation() }
            aggregation.totalRating += rating.rate
            aggregation.totalCount += 1
            if (userId != null && userId == rating.userId) {
                aggregation.ratingByUser = rating.rate
            }
        }
        return aggregationMap
    }

    private data class RatingAggregation(
        var totalRating: Int = 0,
        var totalCount: Int = 0,
        var ratingByUser: Int? = null
    ) {
        fun toJson(): JsonObject {
            val `object` = JsonObject()
            if (ratingByUser != null) {
                `object`.addProperty("ratingByUser", ratingByUser)
            }
            if (totalCount > 0) {
                `object`.addProperty("averageRating", totalRating.toFloat() / totalCount)
                `object`.addProperty("total", totalCount)
            }
            return `object`
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
