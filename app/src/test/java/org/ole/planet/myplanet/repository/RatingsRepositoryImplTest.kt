package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.room.dao.RatingDao
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.model.RealmUser

@OptIn(ExperimentalCoroutinesApi::class)
class RatingsRepositoryImplTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var ratingDao: RatingDao
    private lateinit var gson: Gson
    private lateinit var repository: RatingsRepositoryImpl

    @Before
    fun setup() {
        Logger.getLogger("io.mockk").level = Level.OFF
        databaseService = mockk(relaxed = true)
        ratingDao = mockk(relaxed = true)
        gson = Gson()

        repository = RatingsRepositoryImpl(
            databaseService,
            UnconfinedTestDispatcher(),
            gson,
            ratingDao
        )
    }

    // Mocks the RealmUser lookup used by findUserForRating (RealmUser is still on Realm).
    private fun mockUserLookup(user: RealmUser?) {
        val realm = mockk<io.realm.Realm>(relaxed = true)
        val userQuery = mockk<io.realm.RealmQuery<RealmUser>>(relaxed = true)
        every { realm.where(RealmUser::class.java) } returns userQuery
        every { userQuery.equalTo(any<String>(), any<String>()) } returns userQuery
        every { userQuery.findFirst() } returns user
        if (user != null) every { realm.copyFromRealm(user) } returns user
        coEvery { databaseService.withRealmAsync<Any?>(any()) } answers {
            firstArg<(io.realm.Realm) -> Any?>().invoke(realm)
        }
    }

    @Test
    fun `getRatings aggregates ratings properly`() = runTest {
        val rating1 = RealmRating().apply { type = "course"; item = "course1"; rate = 4; userId = "user1" }
        val rating2 = RealmRating().apply { type = "course"; item = "course1"; rate = 5; userId = "user2" }
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
    fun `getRatingsById returns specific aggregated rating`() = runTest {
        val rating = RealmRating().apply { type = "course"; item = "course1"; rate = 5; userId = "user1" }
        coEvery { ratingDao.getByTypeAndItem("course", "course1") } returns listOf(rating)

        val result = repository.getRatingsById("course", "course1", "user1")

        assertNotNull(result)
        assertEquals(5, result!!.get("ratingByUser").asInt)
        assertEquals(1, result.get("total").asInt)
        assertEquals(5.0f, result.get("averageRating").asFloat)
    }

    @Test
    fun `getCourseRatings returns aggregated course ratings`() = runTest {
        val rating = RealmRating().apply { type = "course"; item = "course1"; rate = 3; userId = "user1" }
        coEvery { ratingDao.getByType("course") } returns listOf(rating)

        val result = repository.getCourseRatings("user1")

        assertEquals(1, result.size)
        assertNotNull(result["course1"])
    }

    @Test
    fun `getResourceRatings returns aggregated resource ratings`() = runTest {
        val rating = RealmRating().apply { type = "resource"; item = "resource1"; rate = 5; userId = "user1" }
        coEvery { ratingDao.getByType("resource") } returns listOf(rating)

        val result = repository.getResourceRatings("user1")

        assertEquals(1, result.size)
        assertNotNull(result["resource1"])
    }

    @Test
    fun `getRatingSummary returns correct summary`() = runTest {
        val userRating = RealmRating().apply { id = "rating1"; rate = 5; comment = "Great"; userId = "user1" }
        val other = RealmRating().apply { id = "rating2"; rate = 4; userId = "user2" }
        coEvery { ratingDao.getByTypeAndItem("course", "course1") } returns listOf(userRating, other)

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
        coEvery { ratingDao.getByTypeAndItem("course", "course1") } returns emptyList()

        val summary = repository.getRatingSummary("course", "course1", "user1")

        assertEquals(0, summary.totalRatings)
        assertEquals(0f, summary.averageRating)
        assertEquals(null, summary.userRating)
        assertEquals(null, summary.existingRating)
    }

    @Test
    fun `submitRating inserts new rating if not exists`() = runTest {
        mockUserLookup(RealmUser().apply { id = "user1"; _id = "user1" })
        coEvery { ratingDao.findByTypeUserItem("course", "user1", "course1") } returns null
        val savedSlot = slot<RealmRating>()
        coEvery { ratingDao.upsert(capture(savedSlot)) } returns Unit
        coEvery { ratingDao.getByTypeAndItem("course", "course1") } returns listOf(
            RealmRating().apply { rate = 4; userId = "user1" }
        )

        val summary = repository.submitRating("course", "course1", "Good", "user1", 4f, "Nice")

        coVerify { ratingDao.upsert(any()) }
        assertEquals("Nice", savedSlot.captured.comment)
        assertEquals(4, savedSlot.captured.rate)
        assertEquals(1, summary.totalRatings)
        assertEquals(4.0f, summary.averageRating)
        assertEquals(4, summary.userRating)
    }

    @Test
    fun `submitRating updates existing rating if it exists`() = runTest {
        mockUserLookup(RealmUser().apply { id = "user1"; _id = "user1" })
        val existingRating = RealmRating().apply { id = "existing_id"; rate = 3 }
        coEvery { ratingDao.findByTypeUserItem("course", "user1", "course1") } returns existingRating
        coEvery { ratingDao.findById("existing_id") } returns existingRating
        coEvery { ratingDao.update(any()) } returns Unit
        coEvery { ratingDao.getByTypeAndItem("course", "course1") } returns listOf(existingRating)

        val summary = repository.submitRating("course", "course1", "Updated", "user1", 5f, "Awesome")

        assertEquals(5, existingRating.rate)
        assertEquals("Awesome", existingRating.comment)
        assertEquals("Updated", existingRating.title)
        coVerify { ratingDao.update(existingRating) }
        assertEquals(1, summary.totalRatings)
        assertEquals(5.0f, summary.averageRating)
        assertEquals(5, summary.userRating)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `submitRating throws when userId is blank`() = runTest {
        repository.submitRating("course", "course1", "Title", "", 4f, "Comment")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `submitRating throws when user is not found`() = runTest {
        mockUserLookup(null)
        repository.submitRating("course", "course1", "Title", "unknown", 4f, "Comment")
    }

    @Test
    fun `insertRatingsFromSync upserts mapped entities`() = runTest {
        val savedSlot = slot<List<RealmRating>>()
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
}
