package org.ole.planet.myplanet.repository

import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject as KJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.model.DocumentResponse
import org.ole.planet.myplanet.model.Rows
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserDataUploadScheduler
import org.ole.planet.myplanet.services.UserDataWorker
import org.ole.planet.myplanet.services.sync.TransactionSyncManager
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.SyncTimeLogger
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.TestTimeProvider
import org.ole.planet.myplanet.utils.TimeProvider
import org.ole.planet.myplanet.utils.UrlUtils
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class SyncRepositoryImplTest {

    private val apiInterface: ApiInterface = mockk(relaxed = true)
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider: DispatcherProvider = TestDispatcherProvider(testDispatcher)
    private val resourcesRepository: ResourcesRepository = mockk(relaxed = true)
    private val coursesRepository: CoursesRepository = mockk(relaxed = true)
    private val eventsRepository: EventsSyncWriter = mockk(relaxed = true)
    private val teamsSyncRepository: TeamsSyncRepository = mockk(relaxed = true)
    private val userSyncRepository: dagger.Lazy<UserSyncRepository> = mockk(relaxed = true)
    private val transactionSyncManager: dagger.Lazy<TransactionSyncManager> = mockk(relaxed = true)
    private val syncTimeLogger: SyncTimeLogger = mockk(relaxed = true)
    private val userDataUploadScheduler: UserDataUploadScheduler = mockk(relaxed = true)

    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var timeProvider: TimeProvider
    private lateinit var syncRepository: SyncRepositoryImpl

    private val storedStringMap = mutableMapOf<String, String>()
    private val storedLongMap = mutableMapOf<String, Long>()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.isLoggable(any(), any()) } returns false
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0

        sharedPrefManager = mockk(relaxed = true)
        timeProvider = TestTimeProvider(1000L)

        storedStringMap.clear()
        storedLongMap.clear()

        every { sharedPrefManager.getRawString(any(), any()) } answers {
            val key = firstArg<String>()
            val default = secondArg<String>()
            storedStringMap[key] ?: default
        }
        every { sharedPrefManager.getRawLong(any(), any()) } answers {
            val key = firstArg<String>()
            val default = secondArg<Long>()
            storedLongMap[key] ?: default
        }
        every { sharedPrefManager.setRawString(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<String>()
            storedStringMap[key] = value
        }
        every { sharedPrefManager.setRawLong(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<Long>()
            storedLongMap[key] = value
        }

        every { sharedPrefManager.getUrlUser() } returns "user"
        every { sharedPrefManager.getUrlPwd() } returns "pass"
        every { sharedPrefManager.getCouchdbUrl() } returns "http://localhost:5984"
        UrlUtils.init(sharedPrefManager)

        syncRepository = SyncRepositoryImpl(
            apiInterface = apiInterface,
            dispatcherProvider = dispatcherProvider,
            resourcesRepository = resourcesRepository,
            coursesRepository = coursesRepository,
            eventsRepository = eventsRepository,
            teamsSyncRepository = teamsSyncRepository,
            userSyncRepository = userSyncRepository,
            transactionSyncManager = transactionSyncManager,
            syncTimeLogger = syncTimeLogger,
            sharedPrefManager = sharedPrefManager,
            timeProvider = timeProvider,
            userDataUploadScheduler = userDataUploadScheduler
        )
    }

    @After
    fun tearDown() {
        UrlUtils.resetForTesting()
        unmockkAll()
    }

    @Test
    fun `uploadLoginData delegates to UserDataUploadScheduler`() {
        val expectedFlow = flowOf(SyncUiState.Loading)
        every {
            userDataUploadScheduler.enqueueUserDataUpload("UploadUserData_Login", UserDataWorker.UPLOAD_TYPE_LOGIN)
        } returns expectedFlow

        val result = syncRepository.uploadLoginData()

        assertEquals(expectedFlow, result)
        verify(exactly = 1) {
            userDataUploadScheduler.enqueueUserDataUpload("UploadUserData_Login", UserDataWorker.UPLOAD_TYPE_LOGIN)
        }
    }

    @Test
    fun `uploadBulkData delegates to UserDataUploadScheduler`() {
        val expectedFlow = flowOf(SyncUiState.Loading)
        every {
            userDataUploadScheduler.enqueueUserDataUpload("UploadUserData_Bulk", UserDataWorker.UPLOAD_TYPE_BULK)
        } returns expectedFlow

        val result = syncRepository.uploadBulkData()

        assertEquals(expectedFlow, result)
        verify(exactly = 1) {
            userDataUploadScheduler.enqueueUserDataUpload("UploadUserData_Bulk", UserDataWorker.UPLOAD_TYPE_BULK)
        }
    }

    @Test
    fun `processShelfParallel dispatches known shelf types to correct repositories`() = runTest {
        val shelfId = "shelf123"

        val shelfDoc = buildJsonObject {
            put("_id", shelfId)
            putJsonArray("resourceIds") { add("res1") }
            putJsonArray("courseIds") { add("course1") }
            putJsonArray("meetupIds") { add("meetup1") }
            putJsonArray("myTeamIds") { add("team1") }
        }

        coEvery {
            apiInterface.getJsonObject(any(), any())
        } answers {
            Response.success(shelfDoc)
        }

        fun createDocResponse(id: String): Response<KJsonObject> {
            val doc = buildJsonObject { put("_id", id) }
            val row = buildJsonObject { put("doc", doc) }
            val rows = buildJsonArray { add(row) }
            val body = buildJsonObject { put("rows", rows) }
            return Response.success(body)
        }

        coEvery {
            apiInterface.postDoc(any(), any(), any(), any())
        } answers {
            val url = thirdArg<String>()
            when {
                url.contains("resources") -> createDocResponse("res1")
                url.contains("courses") -> createDocResponse("course1")
                url.contains("meetups") -> createDocResponse("meetup1")
                url.contains("teams") -> createDocResponse("team1")
                else -> Response.success(KJsonObject(emptyMap()))
            }
        }

        coEvery { resourcesRepository.batchInsertMyLibrary(shelfId, any()) } returns 1
        coEvery { coursesRepository.batchInsertMyCourses(shelfId, any()) } returns 1
        coEvery { eventsRepository.batchInsertMeetups(any()) } returns 1
        coEvery { teamsSyncRepository.batchInsertMyTeams(any()) } returns 1

        val totalProcessed = syncRepository.processShelfParallel(shelfId)

        assertEquals(4, totalProcessed)
        coVerify(exactly = 1) { resourcesRepository.batchInsertMyLibrary(shelfId, any()) }
        coVerify(exactly = 1) { coursesRepository.batchInsertMyCourses(shelfId, any()) }
        coVerify(exactly = 1) { eventsRepository.batchInsertMeetups(any()) }
        coVerify(exactly = 1) { teamsSyncRepository.batchInsertMyTeams(any()) }
    }

    @Test
    fun `processShelfParallel handles unknown shelf type by performing no dispatch`() = runTest {
        val shelfId = "shelfUnknown"

        val shelfDoc = buildJsonObject {
            putJsonArray("unknownKey") { add("unknownItem1") }
        }

        coEvery {
            apiInterface.getJsonObject(any(), any())
        } answers {
            Response.success(shelfDoc)
        }

        coEvery {
            apiInterface.postDoc(any(), any(), any(), any())
        } answers {
            Response.success(buildJsonObject {
                val doc = buildJsonObject { put("_id", "unknownItem1") }
                val row = buildJsonObject { put("doc", doc) }
                putJsonArray("rows") { add(row) }
            })
        }

        val customShelfData = Constants.ShelfData("unknownKey", "unknownType", "unknownCategoryKey")
        val originalList = ArrayList(Constants.shelfDataList)
        try {
            Constants.shelfDataList.add(customShelfData)
            val totalProcessed = syncRepository.processShelfParallel(shelfId)
            assertEquals(0, totalProcessed)
            coVerify(exactly = 0) { resourcesRepository.batchInsertMyLibrary(any(), any()) }
            coVerify(exactly = 0) { coursesRepository.batchInsertMyCourses(any(), any()) }
            coVerify(exactly = 0) { eventsRepository.batchInsertMeetups(any()) }
            coVerify(exactly = 0) { teamsSyncRepository.batchInsertMyTeams(any()) }
        } finally {
            Constants.shelfDataList.clear()
            Constants.shelfDataList.addAll(originalList)
        }
    }

    @Test
    fun `getCachedShelvesWithData returns stored list when cache is within 6 hours`() {
        val now = 1000000000000L
        val cacheTime = now - (5 * 60 * 60 * 1000L) // 5 hours ago
        (timeProvider as TestTimeProvider).currentTime = now

        storedLongMap["shelves_cache_time"] = cacheTime
        storedStringMap["shelves_with_data"] = "shelf1,shelf2,shelf3"

        val result = syncRepository.getCachedShelvesWithData()

        assertEquals(listOf("shelf1", "shelf2", "shelf3"), result)
    }

    @Test
    fun `getCachedShelvesWithData returns empty list when cache is older than 6 hours`() {
        val now = 1000000000000L
        val cacheTime = now - (6 * 60 * 60 * 1000L + 1L) // 6 hours and 1 millisecond ago
        (timeProvider as TestTimeProvider).currentTime = now

        storedLongMap["shelves_cache_time"] = cacheTime
        storedStringMap["shelves_with_data"] = "shelf1,shelf2,shelf3"

        val result = syncRepository.getCachedShelvesWithData()

        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `getCachedShelvesWithData returns empty list when cache time is not set`() {
        val now = 1000000000000L
        (timeProvider as TestTimeProvider).currentTime = now

        val result = syncRepository.getCachedShelvesWithData()

        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `cacheShelvesWithData stores comma joined string and current timestamp`() {
        val now = 1000000000000L
        (timeProvider as TestTimeProvider).currentTime = now

        val shelves = listOf("shelf_a", "shelf_b", "shelf_c")
        syncRepository.cacheShelvesWithData(shelves)

        assertEquals("shelf_a,shelf_b,shelf_c", storedStringMap["shelves_with_data"])
        assertEquals(now, storedLongMap["shelves_cache_time"])
    }

    @Test
    fun `roundtrip caching and retrieving preserves shelf list`() {
        val now = 1000000000000L
        (timeProvider as TestTimeProvider).currentTime = now

        val inputShelves = listOf("shelf_1", "shelf_2", "shelf_3")
        syncRepository.cacheShelvesWithData(inputShelves)

        val retrievedShelves = syncRepository.getCachedShelvesWithData()

        assertEquals(inputShelves, retrievedShelves)
    }

    @Test
    fun `syncDashboardKeyId rethrows CancellationException instead of returning an error state`() = runTest {
        coEvery {
            transactionSyncManager.get().syncDashboardKeyId(any())
        } throws CancellationException("Dashboard sync cancelled")

        var rethrown = false
        var state: SyncUiState? = null
        try {
            state = syncRepository.syncDashboardKeyId("learner")
        } catch (_: CancellationException) {
            rethrown = true
        }

        assertTrue("expected CancellationException to propagate, got state $state", rethrown)
    }

    @Test(expected = CancellationException::class)
    fun `processShelfParallel rethrows CancellationException when shelf dispatch handler fails with CancellationException`() = runTest {
        val shelfId = "shelf123"
        val shelfDoc = buildJsonObject {
            put("_id", shelfId)
            putJsonArray("resourceIds") { add("res1") }
        }

        coEvery {
            apiInterface.getJsonObject(any(), match { it.contains("/shelf/$shelfId") })
        } returns Response.success(shelfDoc)

        val doc1 = buildJsonObject { put("_id", "res1") }
        val row1 = buildJsonObject { put("doc", doc1) }
        val rows = buildJsonArray { add(row1) }
        val body = buildJsonObject { put("rows", rows) }
        coEvery {
            apiInterface.postDoc(any(), any(), any(), any())
        } returns Response.success(body)

        coEvery {
            resourcesRepository.batchInsertMyLibrary(shelfId, any())
        } throws CancellationException("Shelf insertion cancelled")

        syncRepository.processShelfParallel(shelfId)
    }

    @Test
    fun `getShelvesWithData returns cached shelves on cache hit without making network calls`() = runTest {
        val now = 1000000000000L
        (timeProvider as TestTimeProvider).currentTime = now
        storedLongMap["shelves_cache_time"] = now - 1000L
        storedStringMap["shelves_with_data"] = "shelf1,shelf2"

        val result = syncRepository.getShelvesWithData()

        assertEquals(listOf("shelf1", "shelf2"), result)
        coVerify(exactly = 0) { apiInterface.getDocuments(any(), any()) }
    }

    @Test
    fun `getShelvesWithData on cache miss fetches documents, performs batch check, caches, and returns result`() = runTest {
        val now = 1000000000000L
        (timeProvider as TestTimeProvider).currentTime = now

        val docResponse = DocumentResponse().apply {
            rows = listOf(
                Rows().apply { id = "shelf1" },
                Rows().apply { id = "shelf2" }
            )
        }
        coEvery { apiInterface.getDocuments(any(), any()) } returns Response.success(docResponse)
        coEvery { userSyncRepository.get().checkShelfBatchForDataOptimized(listOf("shelf1", "shelf2")) } returns listOf("shelf1")

        val result = syncRepository.getShelvesWithData()

        assertEquals(listOf("shelf1"), result)
        coVerify(exactly = 1) { apiInterface.getDocuments(any(), any()) }
        coVerify(exactly = 1) { userSyncRepository.get().checkShelfBatchForDataOptimized(listOf("shelf1", "shelf2")) }
        assertEquals("shelf1", storedStringMap["shelves_with_data"])
        assertEquals(now, storedLongMap["shelves_cache_time"])
    }

    @Test
    fun `getShelvesWithData on all_docs failure returns empty list without caching`() = runTest {
        val now = 1000000000000L
        (timeProvider as TestTimeProvider).currentTime = now

        coEvery { apiInterface.getDocuments(any(), any()) } returns Response.error(500, okhttp3.ResponseBody.create(null, ""))

        val result = syncRepository.getShelvesWithData()

        assertEquals(emptyList<String>(), result)
        coVerify(atLeast = 1) { apiInterface.getDocuments(any(), any()) }
        coVerify(exactly = 0) { userSyncRepository.get().checkShelfBatchForDataOptimized(any()) }
        assertEquals(null, storedStringMap["shelves_with_data"])
    }

    @Test
    fun `fetchResourceTotalRows returns 42 for total_rows 42`() = runTest {
        val body = buildJsonObject { put("total_rows", 42) }
        coEvery { apiInterface.getJsonObject(any(), match { it.contains("resources/_all_docs?limit=0") }) } returns Response.success(body)

        val result = syncRepository.fetchResourceTotalRows()

        assertEquals(42, result)
    }

    @Test
    fun `fetchResourceTotalRows returns null for empty object`() = runTest {
        val body = buildJsonObject {}
        coEvery { apiInterface.getJsonObject(any(), match { it.contains("resources/_all_docs?limit=0") }) } returns Response.success(body)

        val result = syncRepository.fetchResourceTotalRows()

        assertEquals(null, result)
    }

    @Test
    fun `fetchResourceTotalRows returns null for a null response`() = runTest {
        coEvery { apiInterface.getJsonObject(any(), match { it.contains("resources/_all_docs?limit=0") }) } throws RuntimeException("Network error")

        val result = syncRepository.fetchResourceTotalRows()

        assertEquals(null, result)
    }

    @Test
    fun `fetchResourceRows hits URL containing limit and skip and returns rows array`() = runTest {
        val row1 = buildJsonObject { put("id", "res1") }
        val body = buildJsonObject {
            putJsonArray("rows") { add(row1) }
        }
        coEvery {
            apiInterface.getJsonObject(any(), match { it.contains("resources/_all_docs?include_docs=true&limit=7&skip=14") })
        } returns Response.success(body)

        val rows = syncRepository.fetchResourceRows(7, 14)

        assertEquals(1, rows?.size())
        assertEquals("res1", rows?.get(0)?.asJsonObject?.get("id")?.asString)
    }

    @Test
    fun `fetchResourceRows returns null for a null response`() = runTest {
        coEvery { apiInterface.getJsonObject(any(), match { it.contains("resources/_all_docs?include_docs=true") }) } throws RuntimeException("Network error")

        val rows = syncRepository.fetchResourceRows(7, 14)

        assertEquals(null, rows)
    }

    @Test
    fun `filterSyncableResourceDocs drops design docs, blank ids and rows without doc`() {
        val rows = com.google.gson.JsonArray()

        // _design doc
        val rowDesign = com.google.gson.JsonObject().apply {
            add("doc", com.google.gson.JsonObject().apply { addProperty("_id", "_design/resources") })
        }
        // blank id
        val rowBlank = com.google.gson.JsonObject().apply {
            add("doc", com.google.gson.JsonObject().apply { addProperty("_id", "   ") })
        }
        // no doc field
        val rowNoDoc = com.google.gson.JsonObject().apply {
            addProperty("key", "val")
        }
        // valid doc 1
        val doc1 = com.google.gson.JsonObject().apply { addProperty("_id", "res_1") }
        val rowValid1 = com.google.gson.JsonObject().apply { add("doc", doc1) }

        // valid doc 2
        val doc2 = com.google.gson.JsonObject().apply { addProperty("_id", "res_2") }
        val rowValid2 = com.google.gson.JsonObject().apply { add("doc", doc2) }

        rows.add(rowDesign)
        rows.add(rowBlank)
        rows.add(rowNoDoc)
        rows.add(rowValid1)
        rows.add(rowValid2)

        val filtered = syncRepository.filterSyncableResourceDocs(rows)

        assertEquals(2, filtered.size)
        assertEquals("res_1", filtered[0].get("_id").asString)
        assertEquals("res_2", filtered[1].get("_id").asString)
    }

    @Test
    fun `processShelfParallel logs per-type failure when batch insert throws`() = runTest {
        val shelfId = "shelfErrorInner"
        val shelfDoc = buildJsonObject {
            put("_id", shelfId)
            putJsonArray("resourceIds") { add("res1") }
        }

        coEvery {
            apiInterface.getJsonObject(any(), match { it.contains("/shelf/$shelfId") })
        } returns Response.success(shelfDoc)

        val doc1 = buildJsonObject { put("_id", "res1") }
        val row1 = buildJsonObject { put("doc", doc1) }
        val rows = buildJsonArray { add(row1) }
        val body = buildJsonObject { put("rows", rows) }
        coEvery {
            apiInterface.postDoc(any(), any(), any(), any())
        } returns Response.success(body)

        coEvery {
            resourcesRepository.batchInsertMyLibrary(shelfId, any())
        } throws IllegalStateException("db insertion failed")

        val result = syncRepository.processShelfParallel(shelfId)

        assertEquals(0, result)
        verify(exactly = 1) {
            syncTimeLogger.logDetail("shelf_sync", match { it.contains("failed") })
        }
    }

    @Test
    fun `processShelfParallel logs top-level failure to syncTimeLogger when outer processing throws`() = runTest {
        val shelfId = "shelfErrorOuter"

        io.mockk.mockkObject(org.ole.planet.myplanet.data.api.ApiClient)
        coEvery {
            org.ole.planet.myplanet.data.api.ApiClient.executeWithRetryAndWrap<Any>(any())
        } throws IllegalStateException("boom")

        val result = syncRepository.processShelfParallel(shelfId)

        assertEquals(0, result)
        val capturedMessages = mutableListOf<String>()
        verify(exactly = 1) {
            syncTimeLogger.logDetail("shelf_sync", capture(capturedMessages))
        }
        assertEquals("Shelf $shelfId processing failed: IllegalStateException", capturedMessages.first())
    }
}
