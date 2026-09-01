package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.RatingAggregate
import org.ole.planet.myplanet.data.room.dao.RatingDao
import org.ole.planet.myplanet.model.Rating
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.utils.DispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class RatingsRepositoryImplTest {

    private lateinit var ratingDao: RatingDao
    private lateinit var dispatcherProvider: DispatcherProvider
    private lateinit var gson: Gson
    private lateinit var repository: RatingsRepositoryImpl

    @Before
    fun setup() {
        Logger.getLogger("io.mockk").level = Level.OFF
        ratingDao = mockk(relaxed = true)
        val testDispatcher = StandardTestDispatcher()
        dispatcherProvider = object : DispatcherProvider {
            override val main: CoroutineDispatcher = testDispatcher
            override val io: CoroutineDispatcher = testDispatcher
            override val default: CoroutineDispatcher = testDispatcher
            override val unconfined: CoroutineDispatcher = testDispatcher
        }
        gson = Gson()

        repository = RatingsRepositoryImpl(gson, ratingDao, dispatcherProvider)
    }

    @Test
    fun `getRatings aggregates ratings properly`() = runTest {
        val rating1 = Rating().apply { type = "course"; item = "course1"; rate = 4; userId = "user1" }
        val rating2 = Rating().apply { type = "course"; item = "course1"; rate = 5; userId = "user2" }
        coEvery { ratingDao.getByType("course") } returns listOf(rating1, rating2)

        val result = repository.getRatings("course", "user1")

        assertEquals(1, result.size)
        val aggregated = result["course1"]
        assertNotNull(aggregated)
        assertEquals(4, aggregated!!.get("ratingByUser").asInt)
        assertEquals(2, aggregated.get("total").asInt)
        assertEquals(4.5f, aggregated.get("averageRating").asFloat)
    }

    @Test
    fun `getRatingsById returns specific aggregated rating summary`() = runTest {
        val rating = Rating().apply { id = "rating1"; type = "course"; item = "course1"; rate = 5; userId = "user1"; comment = "Great" }
        coEvery { ratingDao.getAggregate("course", "course1") } returns RatingAggregate(1, 5.0)
        coEvery { ratingDao.findByTypeUserItem("course", "user1", "course1") } returns rating

        val result = repository.getRatingsById("course", "course1", "user1")

        assertNotNull(result)
        assertEquals(5, result!!.userRating)
        assertEquals(1, result.totalRatings)
        assertEquals(5.0f, result.averageRating)
        assertEquals("Great", result.existingRating?.comment)
    }

    @Test
    fun `getCourseRatings returns aggregated course ratings`() = runTest {
        val rating = Rating().apply { type = "course"; item = "course1"; rate = 3; userId = "user1" }
        coEvery { ratingDao.getByType("course") } returns listOf(rating)

        val result = repository.getCourseRatings("user1")

        assertEquals(1, result.size)
        assertNotNull(result["course1"])
    }

    @Test
    fun `getResourceRatings returns aggregated resource ratings`() = runTest {
        val rating = Rating().apply { type = "resource"; item = "resource1"; rate = 5; userId = "user1" }
        coEvery { ratingDao.getByType("resource") } returns listOf(rating)

        val result = repository.getResourceRatings("user1")

        assertEquals(1, result.size)
        assertNotNull(result["resource1"])
    }

    @Test
    fun `getRatingSummary returns correct summary`() = runTest {
        val userRating = Rating().apply { id = "rating1"; rate = 5; comment = "Great"; userId = "user1" }
        coEvery { ratingDao.getAggregate("course", "course1") } returns RatingAggregate(2, 4.5)
        coEvery { ratingDao.findByTypeUserItem("course", "user1", "course1") } returns userRating

        val summary = repository.getRatingSummary("course", "course1", "user1")

        assertEquals(2, summary.totalRatings)
        assertEquals(4.5f, summary.averageRating)
        assertEquals(5, summary.userRating)
        assertEquals("rating1", summary.existingRating?.id)
        assertEquals("Great", summary.existingRating?.comment)
        assertEquals(5, summary.existingRating?.rate)
    }

    @Test
    fun `getRatingSummary handles zero ratings correctly`() = runTest {
        coEvery { ratingDao.getAggregate("course", "course1") } returns RatingAggregate(0, null)
        coEvery { ratingDao.findByTypeUserItem("course", "user1", "course1") } returns null

        val summary = repository.getRatingSummary("course", "course1", "user1")

        assertEquals(0, summary.totalRatings)
        assertEquals(0f, summary.averageRating)
        assertEquals(null, summary.userRating)
        assertEquals(null, summary.existingRating)
    }

    @Test
    fun `getRatingSummary with null userId omits user lookup`() = runTest {
        coEvery { ratingDao.getAggregate("course", "course1") } returns RatingAggregate(3, 4.0)

        val summary = repository.getRatingSummary("course", "course1", null)

        assertEquals(3, summary.totalRatings)
        assertEquals(4.0f, summary.averageRating)
        assertEquals(null, summary.userRating)
        assertEquals(null, summary.existingRating)
        coVerify(exactly = 0) { ratingDao.findByTypeUserItem(any(), any(), any()) }
    }

    @Test
    fun `submitRating inserts new rating if not exists`() = runTest {
        val testUser = UserEntity(id = "user1", _id = "user1", parentCode = "parent", planetCode = "planet")
        val savedRating = Rating().apply { rate = 4; userId = "user1"; comment = "Nice" }
        coEvery { ratingDao.findByTypeUserItem("course", "user1", "course1") } returnsMany listOf(null, savedRating)
        val savedSlot = slot<Rating>()
        coEvery { ratingDao.upsert(capture(savedSlot)) } returns Unit
        coEvery { ratingDao.getAggregate("course", "course1") } returns RatingAggregate(1, 4.0)

        val summary = repository.submitRating("course", "course1", "Good", testUser, 4f, "Nice")

        coVerify { ratingDao.upsert(any()) }
        assertEquals("Nice", savedSlot.captured.comment)
        assertEquals(4, savedSlot.captured.rate)
        assertEquals(1, summary.totalRatings)
        assertEquals(4.0f, summary.averageRating)
        assertEquals(4, summary.userRating)
    }

    @Test
    fun `submitRating updates existing rating if it exists`() = runTest {
        val testUser = UserEntity(id = "user1", _id = "user1", parentCode = "parent", planetCode = "planet")
        val existingRating = Rating().apply { id = "existing_id"; rate = 3 }
        coEvery { ratingDao.findByTypeUserItem("course", "user1", "course1") } returns existingRating
        coEvery { ratingDao.findById("existing_id") } returns existingRating
        coEvery { ratingDao.update(any()) } returns Unit
        coEvery { ratingDao.getAggregate("course", "course1") } returns RatingAggregate(1, 5.0)

        val summary = repository.submitRating("course", "course1", "Updated", testUser, 5f, "Awesome")

        assertEquals(5, existingRating.rate)
        assertEquals("Awesome", existingRating.comment)
        assertEquals("Updated", existingRating.title)
        coVerify { ratingDao.update(existingRating) }
        assertEquals(1, summary.totalRatings)
        assertEquals(5.0f, summary.averageRating)
        assertEquals(5, summary.userRating)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `submitRating throws when user id is blank`() = runTest {
        val blankUser = UserEntity(id = "", _id = "")
        repository.submitRating("course", "course1", "Title", blankUser, 4f, "Comment")
    }

    @Test
    fun `insertRatingsFromSync upserts mapped entities`() = runTest {
        val savedSlot = slot<List<Rating>>()
        coEvery { ratingDao.upsertAll(capture(savedSlot)) } returns Unit

        val docs = listOf(
            JsonObject().apply {
                addProperty("_id", "rating1")
                addProperty("rate", 4)
                add("user", JsonObject().apply { addProperty("_id", "user1") })
            }
        )
        repository.insertRatingsFromSync(docs)

        coVerify(exactly = 1) { ratingDao.upsertAll(any()) }
        assertEquals(1, savedSlot.captured.size)
        assertEquals("rating1", savedSlot.captured[0].id)
        assertEquals("user1", savedSlot.captured[0].userId)
    }

    @Test
    fun `insertRatingsFromSync strips user attachments to avoid oversized rows`() = runTest {
        val savedSlot = slot<List<Rating>>()
        coEvery { ratingDao.upsertAll(capture(savedSlot)) } returns Unit

        val docs = listOf(
            JsonObject().apply {
                addProperty("_id", "rating1")
                add("user", JsonObject().apply {
                    addProperty("_id", "user1")
                    add("_attachments", JsonObject().apply {
                        add("img.png", JsonObject().apply { addProperty("data", "AAAA") })
                    })
                })
            }
        )
        repository.insertRatingsFromSync(docs)

        val storedUser = savedSlot.captured[0].user
        assertNotNull(storedUser)
        // Regression: a base64 _attachments blob could push a row past SQLite's ~2MB
        // CursorWindow limit and crash getByType with SQLiteBlobTooBigException.
        org.junit.Assert.assertFalse(storedUser!!.contains("_attachments"))
        assertEquals("user1", savedSlot.captured[0].userId)
    }
}
