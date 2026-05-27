package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.data.queryList
import org.ole.planet.myplanet.data.findCopyByField

@OptIn(ExperimentalCoroutinesApi::class)
class RatingsRepositoryImplTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var repository: RatingsRepositoryImpl
    private lateinit var mockRealm: Realm
    private lateinit var gson: Gson

    @Before
    fun setup() {
        Logger.getLogger("io.mockk").level = Level.OFF
        mockRealm = mockk(relaxed = true)
        databaseService = mockk(relaxed = true)
        gson = Gson()
        mockkStatic(io.realm.log.RealmLog::class)
        every { io.realm.log.RealmLog.error(any<Throwable>(), any<String>(), *anyVararg()) } returns Unit
        every { io.realm.log.RealmLog.error(any<String>(), *anyVararg()) } returns Unit

        coEvery { databaseService.withRealmAsync<Any?>(any()) } answers {
            try {
                val operation = invocation.args[0] as Function1<Realm, Any?>
                operation.invoke(mockRealm)
            } catch(e: Exception) {
                null
            }
        }

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val transaction = firstArg<(Realm) -> Unit>()
            transaction(mockRealm)
        }

        repository = io.mockk.spyk(RatingsRepositoryImpl(
            databaseService,
            UnconfinedTestDispatcher(),
            gson
        ), recordPrivateCalls = true)
    }

    private fun <T : io.realm.RealmObject> mockQueryResults(
        clazz: Class<T>,
        results: List<T>
    ): RealmQuery<T> {
        val mockQuery = mockk<RealmQuery<T>>(relaxed = true)
        val mockResults = mockk<RealmResults<T>>(relaxed = true)

        every { mockRealm.where(clazz) } returns mockQuery

        // Fluent query methods
        mockkStatic("org.ole.planet.myplanet.data.DatabaseServiceKt")
        every { mockQuery.equalTo(any<String>(), any<String>()) } returns mockQuery
        every { mockQuery.equalTo(any<String>(), any<Boolean>()) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults

        every { mockRealm.copyFromRealm(mockResults) } returns results

        // Mock queryList extension
        coEvery { mockRealm.queryList<T>(clazz, any()) } returns results

        // Mock findCopyByField for single objects
        every { mockRealm.findCopyByField<T, String>(clazz, any(), any()) } answers {
            val fieldName = secondArg<String>()
            val value = thirdArg<String>()
            // Find in results
            results.find {
                when(it) {
                    is RealmUser -> (if(fieldName == "id") it.id else it._id) == value
                    is RealmRating -> it.id == value
                    else -> false
                }
            }
        }

        every { mockQuery.findFirst() } answers { results.firstOrNull() }

        return mockQuery
    }

    @Test
    fun `getRatings aggregates ratings properly`() = runTest {
        val ratings = listOf(
            RealmRating().apply { item = "item1"; rate = 4; userId = "user1" },
            RealmRating().apply { item = "item1"; rate = 5; userId = "user2" },
            RealmRating().apply { item = "item2"; rate = 3; userId = "user1" }
        )
        mockQueryResults(RealmRating::class.java, ratings)

        val result = repository.getRatings("course", "user1")

        assertEquals(2, result.size)
        val item1Aggregation = result["item1"]
        assertNotNull(item1Aggregation)
        assertEquals(4, item1Aggregation!!.get("ratingByUser").asInt)
        assertEquals((9f/2f), item1Aggregation.get("averageRating").asFloat)
        assertEquals(2, item1Aggregation.get("total").asInt)
    }

    @Test
    fun `getRatingsById returns specific aggregation`() = runTest {
        val ratings = listOf(
            RealmRating().apply { item = "item1"; rate = 4; userId = "user1" }
        )
        mockQueryResults(RealmRating::class.java, ratings)

        val result = repository.getRatingsById("course", "item1", "user1")

        assertNotNull(result)
        assertEquals(4, result!!.get("ratingByUser").asInt)
        assertEquals((4f/1f), result.get("averageRating").asFloat)
        assertEquals(1, result.get("total").asInt)
    }

    @Test
    fun `getCourseRatings calls getRatings with course type`() = runTest {
        val ratings = listOf(
            RealmRating().apply { item = "course1"; rate = 4; userId = "user1" }
        )
        mockQueryResults(RealmRating::class.java, ratings)

        val result = repository.getCourseRatings("user1")

        assertEquals(1, result.size)
        assertTrue(result.containsKey("course1"))
    }

    @Test
    fun `getResourceRatings calls getRatings with resource type`() = runTest {
        val ratings = listOf(
            RealmRating().apply { item = "resource1"; rate = 5; userId = "user1" }
        )
        mockQueryResults(RealmRating::class.java, ratings)

        val result = repository.getResourceRatings("user1")

        assertEquals(1, result.size)
        assertTrue(result.containsKey("resource1"))
    }

    @Test
    fun `getRatingSummary calculates average and finds user rating`() = runTest {
        val mockQuery = mockk<RealmQuery<RealmRating>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmRating>>(relaxed = true)

        every { mockRealm.where(RealmRating::class.java) } returns mockQuery
        every { mockQuery.equalTo(any<String>(), any<String>()) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults

        every { mockResults.size } returns 3
        every { mockResults.average("rate") } returns 4.0

        val userQuery = mockk<RealmQuery<RealmRating>>(relaxed = true)
        every { mockResults.where() } returns userQuery
        every { userQuery.equalTo("userId", "user1") } returns userQuery

        val existingRating = RealmRating().apply { id = "rating1"; rate = 5; comment = "Great!" }
        every { userQuery.findFirst() } returns existingRating

        val summary = repository.getRatingSummary("course", "item1", "user1")

        assertNotNull(summary)
        assertEquals(3, summary.totalRatings)
        assertEquals(4.0f, summary.averageRating)
        assertEquals(5, summary.userRating)
        assertNotNull(summary.existingRating)
        assertEquals("rating1", summary.existingRating?.id)
        assertEquals(5, summary.existingRating?.rate)
    }

    @Test
    fun `getRatingSummary handles no ratings gracefully`() = runTest {
        val mockQuery = mockk<RealmQuery<RealmRating>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmRating>>(relaxed = true)

        every { mockRealm.where(RealmRating::class.java) } returns mockQuery
        every { mockQuery.equalTo(any<String>(), any<String>()) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults

        every { mockResults.size } returns 0

        val userQuery = mockk<RealmQuery<RealmRating>>(relaxed = true)
        every { mockResults.where() } returns userQuery
        every { userQuery.equalTo("userId", "user1") } returns userQuery
        every { userQuery.findFirst() } returns null

        val summary = repository.getRatingSummary("course", "item1", "user1")

        assertNotNull(summary)
        assertEquals(0, summary.totalRatings)
        assertEquals(0.0f, summary.averageRating)
        assertEquals(null, summary.userRating)
        assertEquals(null, summary.existingRating)
    }


    @Test
    fun `submitRating creates new rating when none exists`() = runTest {
        val user = RealmUser().apply { id = "user1"; parentCode = "p1"; planetCode = "pl1" }
        coEvery { repository["findByField"](RealmUser::class.java, "id", "user1") } returns user
        coEvery { repository["findByField"](RealmUser::class.java, "_id", "user1") } returns null
        // mockQueryResults for findByField (user) and queryList (empty ratings)
        val mockQuery = mockQueryResults(RealmUser::class.java, listOf(user))

        // mock RealmRating queryList returning empty
        val mockRatingQuery = mockk<RealmQuery<RealmRating>>(relaxed = true)
        val mockRatingResults = mockk<RealmResults<RealmRating>>(relaxed = true)
        every { mockRealm.where(RealmRating::class.java) } returns mockRatingQuery
        every { mockRatingQuery.equalTo(any<String>(), any<String>()) } returns mockRatingQuery
        every { mockRatingQuery.findAll() } returns mockRatingResults
        coEvery { mockRealm.queryList<RealmRating>(RealmRating::class.java, any()) } returns emptyList()

        // Mock getting summary at the end
        every { mockRatingResults.size } returns 1
        every { mockRatingResults.average("rate") } returns 4.0
        val summaryUserQuery = mockk<RealmQuery<RealmRating>>(relaxed = true)
        every { mockRatingResults.where() } returns summaryUserQuery
        every { summaryUserQuery.equalTo("userId", "user1") } returns summaryUserQuery
        every { summaryUserQuery.findFirst() } returns RealmRating().apply { rate = 4 }

        val summary = repository.submitRating(
            type = "course",
            itemId = "item1",
            title = "Test Course",
            userId = "user1",
            rating = 4.4f,
            comment = "Good"
        )

        assertNotNull(summary)
        coVerify(exactly = 1) { databaseService.executeTransactionAsync(any()) }
        // Verify copyToRealmOrUpdate was called inside transaction
        verify { mockRealm.copyToRealmOrUpdate(any<RealmRating>()) }
    }

    @Test
    fun `submitRating updates existing rating when it exists`() = runTest {
        val user = RealmUser().apply { id = "user1"; parentCode = "p1"; planetCode = "pl1" }
        coEvery { repository["findByField"](RealmUser::class.java, "id", "user1") } returns user
        coEvery { repository["findByField"](RealmUser::class.java, "_id", "user1") } returns null
        val existingRating = RealmRating().apply { id = "rating1"; rate = 2; comment = "Bad" }

        // mockQueryResults for findByField (user)
        mockQueryResults(RealmUser::class.java, listOf(user))

        // mock queryList for existing rating
        coEvery { mockRealm.queryList<RealmRating>(RealmRating::class.java, any()) } returns listOf(existingRating)

        // For update path, it uses applyEqualTo which we mock via equalTo
        val mockRatingQuery = mockk<RealmQuery<RealmRating>>(relaxed = true)
        every { mockRealm.where(RealmRating::class.java) } returns mockRatingQuery
        every { mockRatingQuery.equalTo(any<String>(), any<String>()) } returns mockRatingQuery
        every { mockRatingQuery.findFirst() } returns existingRating

        // Mock getting summary at the end
        val mockRatingResults = mockk<RealmResults<RealmRating>>(relaxed = true)
        every { mockRatingQuery.findAll() } returns mockRatingResults
        every { mockRatingResults.size } returns 1
        every { mockRatingResults.average("rate") } returns 5.0
        val summaryUserQuery = mockk<RealmQuery<RealmRating>>(relaxed = true)
        every { mockRatingResults.where() } returns summaryUserQuery
        every { summaryUserQuery.equalTo("userId", "user1") } returns summaryUserQuery
        every { summaryUserQuery.findFirst() } returns existingRating

        val summary = repository.submitRating(
            type = "course",
            itemId = "item1",
            title = "Test Course",
            userId = "user1",
            rating = 4.6f, // Should round to 5
            comment = "Excellent now"
        )

        assertNotNull(summary)
        coVerify(exactly = 1) { databaseService.executeTransactionAsync(any()) }

        // verify updater changed the object
        assertEquals("Excellent now", existingRating.comment)
        assertEquals(5, existingRating.rate)
        assertEquals(true, existingRating.isUpdated)
    }


    @Test
    fun `bulkInsertFromSync saves valid json objects`() {
        io.mockk.mockkObject(RealmRating.Companion)
        every { RealmRating.insert(any(), any()) } returns Unit

        val jsonArray = JsonArray()

        // Valid doc
        val validDoc = JsonObject().apply { addProperty("_id", "valid_id") }
        val validWrapper = JsonObject().apply { add("doc", validDoc) }
        jsonArray.add(validWrapper)

        // Invalid design doc
        val designDoc = JsonObject().apply { addProperty("_id", "_design/doc") }
        val designWrapper = JsonObject().apply { add("doc", designDoc) }
        jsonArray.add(designWrapper)

        repository.bulkInsertFromSync(mockRealm, jsonArray)

        verify(exactly = 1) { RealmRating.insert(mockRealm, validDoc) }
        verify(exactly = 0) { RealmRating.insert(mockRealm, designDoc) }

        io.mockk.unmockkObject(RealmRating.Companion)
    }

    @After
    fun tearDown() {
        unmockkStatic("org.ole.planet.myplanet.data.DatabaseServiceKt")
        unmockkStatic(io.realm.log.RealmLog::class)
    }
}
