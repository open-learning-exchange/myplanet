package org.ole.planet.myplanet.repository

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserDataWorker
import org.ole.planet.myplanet.services.sync.TransactionSyncManager
import org.ole.planet.myplanet.services.sync.UserDataUploadScheduler
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

        val shelfDoc = JsonObject().apply {
            add("_id", JsonPrimitive(shelfId))
            add("resourceIds", JsonArray().apply { add("res1") })
            add("courseIds", JsonArray().apply { add("course1") })
            add("meetupIds", JsonArray().apply { add("meetup1") })
            add("myTeamIds", JsonArray().apply { add("team1") })
        }

        coEvery {
            apiInterface.getJsonObject(any(), any())
        } answers {
            Response.success(shelfDoc)
        }

        fun createDocResponse(id: String): Response<JsonObject> {
            val doc = JsonObject().apply { addProperty("_id", id) }
            val row = JsonObject().apply { add("doc", doc) }
            val rows = JsonArray().apply { add(row) }
            val body = JsonObject().apply { add("rows", rows) }
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
                else -> Response.success(JsonObject())
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

        val shelfDoc = JsonObject().apply {
            add("unknownKey", JsonArray().apply { add("unknownItem1") })
        }

        coEvery {
            apiInterface.getJsonObject(any(), any())
        } answers {
            Response.success(shelfDoc)
        }

        coEvery {
            apiInterface.postDoc(any(), any(), any(), any())
        } answers {
            Response.success(JsonObject().apply {
                val doc = JsonObject().apply { addProperty("_id", "unknownItem1") }
                val row = JsonObject().apply { add("doc", doc) }
                add("rows", JsonArray().apply { add(row) })
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
}
