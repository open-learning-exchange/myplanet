package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import io.realm.log.RealmLog
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.model.RealmUser

@OptIn(ExperimentalCoroutinesApi::class)
class RatingsRepositoryImplTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var mockRealm: Realm
    private lateinit var gson: Gson
    private lateinit var repository: RatingsRepositoryImpl

    @Before
    fun setup() {
        Logger.getLogger("io.mockk").level = Level.OFF
        mockkStatic(RealmLog::class)
        every { RealmLog.error(any<Throwable>(), any<String>(), *anyVararg()) } returns Unit
        every { RealmLog.error(any<String>(), *anyVararg()) } returns Unit

        mockRealm = mockk(relaxed = true)
        databaseService = mockk(relaxed = true)
        gson = Gson()

        coEvery { databaseService.withRealmAsync<Any>(any()) } answers {
            val operation = firstArg<(Realm) -> Any>()
            operation(mockRealm)
        }

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val operation = firstArg<(Realm) -> Unit>()
            operation(mockRealm)
        }

        repository = RatingsRepositoryImpl(
            databaseService,
            UnconfinedTestDispatcher(),
            gson
        )
    }

    @After
    fun teardown() {
        unmockkStatic(RealmLog::class)
    }

    private fun mockRatingQuery(vararg ratings: RealmRating): RealmQuery<RealmRating> {
        val mockQuery = mockk<RealmQuery<RealmRating>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmRating>>(relaxed = true)

        every { mockRealm.where(RealmRating::class.java) } returns mockQuery
        every { mockQuery.equalTo(any<String>(), any<String>()) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults

        every { mockRealm.copyFromRealm(mockResults) } returns ratings.toList()

        return mockQuery
    }

    @Test
    fun `getRatings aggregates ratings properly`() = runTest {
        val rating1 = RealmRating().apply {
            type = "course"
            item = "course1"
            rate = 4
            userId = "user1"
        }
        val rating2 = RealmRating().apply {
            type = "course"
            item = "course1"
            rate = 5
            userId = "user2"
        }

        val mockQuery = mockRatingQuery(rating1, rating2)

        val result = repository.getRatings("course", "user1")

        verify { mockQuery.equalTo("type", "course") }

        assertEquals(1, result.size)
        val aggregated = result["course1"]
        assertNotNull(aggregated)
        assertEquals(4, aggregated!!.get("ratingByUser").asInt)
        assertEquals(2, aggregated.get("total").asInt)
        assertEquals(4.5f, aggregated.get("averageRating").asFloat)
    }

    @Test
    fun `getRatingsById returns specific aggregated rating`() = runTest {
        val rating = RealmRating().apply {
            type = "course"
            item = "course1"
            rate = 5
            userId = "user1"
        }
        val mockQuery = mockRatingQuery(rating)

        val result = repository.getRatingsById("course", "course1", "user1")

        verify {
            mockQuery.equalTo("type", "course")
            mockQuery.equalTo("item", "course1")
        }

        assertNotNull(result)
        assertEquals(5, result!!.get("ratingByUser").asInt)
        assertEquals(1, result.get("total").asInt)
        assertEquals(5.0f, result.get("averageRating").asFloat)
    }

    @Test
    fun `getCourseRatings returns aggregated course ratings`() = runTest {
        val rating = RealmRating().apply {
            type = "course"
            item = "course1"
            rate = 3
            userId = "user1"
        }
        val mockQuery = mockRatingQuery(rating)

        val result = repository.getCourseRatings("user1")

        verify { mockQuery.equalTo("type", "course") }
        assertEquals(1, result.size)
        assertNotNull(result["course1"])
    }

    @Test
    fun `getResourceRatings returns aggregated resource ratings`() = runTest {
        val rating = RealmRating().apply {
            type = "resource"
            item = "resource1"
            rate = 5
            userId = "user1"
        }
        val mockQuery = mockRatingQuery(rating)

        val result = repository.getResourceRatings("user1")

        verify { mockQuery.equalTo("type", "resource") }
        assertEquals(1, result.size)
        assertNotNull(result["resource1"])
    }

    @Test
    fun `getRatingSummary returns correct summary`() = runTest {
        val mockQuery = mockk<RealmQuery<RealmRating>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmRating>>(relaxed = true)

        every { mockRealm.where(RealmRating::class.java) } returns mockQuery
        every { mockQuery.equalTo(any<String>(), any<String>()) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults

        every { mockResults.size } returns 2
        every { mockResults.average("rate") } returns 4.5

        val mockSubQuery = mockk<RealmQuery<RealmRating>>(relaxed = true)
        every { mockResults.where() } returns mockSubQuery
        every { mockSubQuery.equalTo("userId", "user1") } returns mockSubQuery

        val userRating = RealmRating().apply {
            id = "rating1"
            rate = 5
            comment = "Great"
        }
        every { mockSubQuery.findFirst() } returns userRating

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
        val mockQuery = mockk<RealmQuery<RealmRating>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmRating>>(relaxed = true)

        every { mockRealm.where(RealmRating::class.java) } returns mockQuery
        every { mockQuery.equalTo(any<String>(), any<String>()) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults

        every { mockResults.size } returns 0

        val mockSubQuery = mockk<RealmQuery<RealmRating>>(relaxed = true)
        every { mockResults.where() } returns mockSubQuery
        every { mockSubQuery.equalTo(any<String>(), any<String>()) } returns mockSubQuery
        every { mockSubQuery.findFirst() } returns null

        val summary = repository.getRatingSummary("course", "course1", "user1")

        assertEquals(0, summary.totalRatings)
        assertEquals(0f, summary.averageRating)
        assertEquals(null, summary.userRating)
        assertEquals(null, summary.existingRating)
    }

    @Test
    fun `submitRating inserts new rating if not exists`() = runTest {
        val mockUserQuery = mockk<RealmQuery<RealmUser>>(relaxed = true)
        every { mockRealm.where(RealmUser::class.java) } returns mockUserQuery
        every { mockUserQuery.equalTo(any<String>(), any<String>()) } returns mockUserQuery
        val mockUser = RealmUser().apply {
            id = "user1"
            _id = "user1"
        }
        every { mockUserQuery.findFirst() } returns mockUser
        every { mockRealm.copyFromRealm(mockUser) } returns mockUser

        var callCount = 0
        every { mockRealm.where(RealmRating::class.java) } answers {
            callCount++
            when (callCount) {
                1 -> { // findFirst in submitRating
                    val query = mockk<RealmQuery<RealmRating>>(relaxed = true)
                    every { query.equalTo(any<String>(), any<String>()) } returns query
                    every { query.findFirst() } returns null
                    query
                }
                else -> { // getRatingSummary
                    val query = mockk<RealmQuery<RealmRating>>(relaxed = true)
                    val results = mockk<RealmResults<RealmRating>>(relaxed = true)
                    every { query.equalTo(any<String>(), any<String>()) } returns query
                    every { query.findAll() } returns results
                    every { results.size } returns 1
                    every { results.average("rate") } returns 4.0
                    val subQuery = mockk<RealmQuery<RealmRating>>(relaxed = true)
                    every { results.where() } returns subQuery
                    every { subQuery.equalTo(any<String>(), any<String>()) } returns subQuery
                    every { subQuery.findFirst() } returns RealmRating().apply { rate = 4 }
                    query
                }
            }
        }

        every { mockRealm.copyToRealmOrUpdate(any<RealmRating>()) } returns mockk()

        val summary = repository.submitRating("course", "course1", "Good", "user1", 4f, "Nice")

        verify { mockRealm.copyToRealmOrUpdate(any<RealmRating>()) }
        assertEquals(1, summary.totalRatings)
        assertEquals(4.0f, summary.averageRating)
        assertEquals(4, summary.userRating)
    }

    @Test
    fun `submitRating updates existing rating if it exists`() = runTest {
        val mockUserQuery = mockk<RealmQuery<RealmUser>>(relaxed = true)
        every { mockRealm.where(RealmUser::class.java) } returns mockUserQuery
        every { mockUserQuery.equalTo(any<String>(), any<String>()) } returns mockUserQuery
        val mockUser = RealmUser().apply {
            id = "user1"
            _id = "user1"
        }
        every { mockUserQuery.findFirst() } returns mockUser
        every { mockRealm.copyFromRealm(mockUser) } returns mockUser

        val existingRating = RealmRating().apply {
            id = "existing_id"
            rate = 3
        }

        var callCount = 0
        every { mockRealm.where(RealmRating::class.java) } answers {
            callCount++
            when (callCount) {
                1 -> { // findFirst in submitRating
                    val query = mockk<RealmQuery<RealmRating>>(relaxed = true)
                    every { query.equalTo(any<String>(), any<String>()) } returns query
                    every { query.findFirst() } returns existingRating
                    every { mockRealm.copyFromRealm(existingRating) } returns existingRating
                    query
                }
                2 -> { // update in submitRating
                    val query = mockk<RealmQuery<RealmRating>>(relaxed = true)
                    every { query.equalTo(any<String>(), any<String>()) } returns query
                    every { query.findFirst() } returns existingRating
                    query
                }
                else -> { // getRatingSummary
                    val query = mockk<RealmQuery<RealmRating>>(relaxed = true)
                    val results = mockk<RealmResults<RealmRating>>(relaxed = true)
                    every { query.equalTo(any<String>(), any<String>()) } returns query
                    every { query.findAll() } returns results
                    every { results.size } returns 1
                    every { results.average("rate") } returns 5.0
                    val subQuery = mockk<RealmQuery<RealmRating>>(relaxed = true)
                    every { results.where() } returns subQuery
                    every { subQuery.equalTo(any<String>(), any<String>()) } returns subQuery
                    every { subQuery.findFirst() } returns existingRating
                    query
                }
            }
        }

        val summary = repository.submitRating("course", "course1", "Updated", "user1", 5f, "Awesome")

        assertEquals(5, existingRating.rate)
        assertEquals("Awesome", existingRating.comment)
        assertEquals("Updated", existingRating.title)
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
        val mockUserQuery = mockk<RealmQuery<RealmUser>>(relaxed = true)
        every { mockRealm.where(RealmUser::class.java) } returns mockUserQuery
        every { mockUserQuery.equalTo(any<String>(), any<String>()) } returns mockUserQuery
        every { mockUserQuery.findFirst() } returns null

        repository.submitRating("course", "course1", "Title", "unknown", 4f, "Comment")
    }

    @Test
    fun `bulkInsertFromSync processes JSON array properly`() = runTest {
        mockkObject(RealmRating.Companion)
        every { RealmRating.insert(any(), any()) } returns Unit

        val jsonArray = JsonArray().apply {
            add(JsonObject().apply {
                add("doc", JsonObject().apply {
                    addProperty("_id", "rating1")
                })
            })
            add(JsonObject().apply {
                add("doc", JsonObject().apply {
                    addProperty("_id", "_design/rating")
                })
            })
        }

        val docs = jsonArray.map { it.asJsonObject.getAsJsonObject("doc") }.filter { !it.get("_id").asString.startsWith("_design") }
        repository.insertRatingsFromSync(docs)

        verify(exactly = 1) { RealmRating.insert(mockRealm, any()) }

        unmockkObject(RealmRating.Companion)
    }
}
