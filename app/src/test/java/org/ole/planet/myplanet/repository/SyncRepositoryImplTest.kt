package org.ole.planet.myplanet.repository

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.TransactionSyncManager
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.SyncTimeLogger
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class SyncRepositoryImplTest {

    private val context: Context = mockk(relaxed = true)
    private val apiInterface: ApiInterface = mockk(relaxed = true)
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider: DispatcherProvider = TestDispatcherProvider(testDispatcher)
    private val resourcesRepository: ResourcesRepository = mockk(relaxed = true)
    private val coursesRepository: CoursesRepository = mockk(relaxed = true)
    private val eventsRepository: EventsSyncWriter = mockk(relaxed = true)
    private val teamsSyncRepository: TeamsSyncRepository = mockk(relaxed = true)
    private val transactionSyncManager: dagger.Lazy<TransactionSyncManager> = mockk(relaxed = true)
    private val syncTimeLogger: SyncTimeLogger = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)

    private lateinit var syncRepository: SyncRepositoryImpl

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.isLoggable(any(), any()) } returns false
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0

        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 1000L

        every { sharedPrefManager.getUrlUser() } returns "user"
        every { sharedPrefManager.getUrlPwd() } returns "pass"
        every { sharedPrefManager.getCouchdbUrl() } returns "http://localhost:5984"
        UrlUtils.init(sharedPrefManager)

        syncRepository = SyncRepositoryImpl(
            context = context,
            apiInterface = apiInterface,
            dispatcherProvider = dispatcherProvider,
            resourcesRepository = resourcesRepository,
            coursesRepository = coursesRepository,
            eventsRepository = eventsRepository,
            teamsSyncRepository = teamsSyncRepository,
            transactionSyncManager = transactionSyncManager,
            syncTimeLogger = syncTimeLogger
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `processShelfParallel dispatches known shelf types to correct repositories`() = runTest {
        val shelfId = "shelf123"

        val shelfDoc = JsonObject().apply {
            add("_id", gsonDocId(shelfId))
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

    private fun gsonDocId(id: String) = com.google.gson.JsonPrimitive(id)
}
