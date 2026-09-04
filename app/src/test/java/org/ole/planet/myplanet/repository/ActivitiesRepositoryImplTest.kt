package org.ole.planet.myplanet.repository

import android.content.Context
import android.provider.Settings
import com.google.gson.JsonObject
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.dao.CourseActivityDao
import org.ole.planet.myplanet.data.room.dao.OfflineActivityDao
import org.ole.planet.myplanet.data.room.dao.RemovedLogDao
import org.ole.planet.myplanet.data.room.dao.ResourceActivityDao
import org.ole.planet.myplanet.data.room.dao.UserChallengeActionsDao
import org.ole.planet.myplanet.model.CourseActivity
import org.ole.planet.myplanet.model.OfflineActivity
import org.ole.planet.myplanet.model.RemovedLog
import org.ole.planet.myplanet.model.ResourceActivity
import org.ole.planet.myplanet.model.UserChallengeActions
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.TimeProvider
import org.ole.planet.myplanet.utils.UrlUtils

@OptIn(ExperimentalCoroutinesApi::class)
class ActivitiesRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var userRepository: UserRepository
    private lateinit var apiInterface: ApiInterface
    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var timeProvider: TimeProvider
    private lateinit var userChallengeActionsDao: UserChallengeActionsDao
    private lateinit var courseActivityDao: CourseActivityDao
    private lateinit var resourceActivityDao: ResourceActivityDao
    private lateinit var offlineActivityDao: OfflineActivityDao
    private lateinit var removedLogDao: RemovedLogDao
    private lateinit var searchActivityDao: org.ole.planet.myplanet.data.room.dao.SearchActivityDao
    private lateinit var userDao: org.ole.planet.myplanet.data.room.dao.UserDao
    private lateinit var dispatcherProvider: DispatcherProvider
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var repository: ActivitiesRepositoryImpl

    @Before
    fun setup() {
        Logger.getLogger("io.mockk").level = Level.OFF
        mockkStatic(Settings.Secure::class)
        every { Settings.Secure.getString(any(), Settings.Secure.ANDROID_ID) } returns "mock_android_id"

        mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "mock_unique_id"
        every { NetworkUtils.getDeviceName() } returns "mock_device"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "mock_custom_device"

        context = mockk(relaxed = true)
        MainApplication.testContext = context
        userRepository = mockk(relaxed = true)
        val lazyUserRepository = Lazy { userRepository }
        apiInterface = mockk(relaxed = true)
        sharedPrefManager = mockk(relaxed = true)
        timeProvider = mockk(relaxed = true)
        userChallengeActionsDao = mockk(relaxed = true)
        courseActivityDao = mockk(relaxed = true)
        resourceActivityDao = mockk(relaxed = true)
        offlineActivityDao = mockk(relaxed = true)
        removedLogDao = mockk(relaxed = true)
        searchActivityDao = mockk(relaxed = true)
        userDao = mockk(relaxed = true)
        dispatcherProvider = TestDispatcherProvider(testDispatcher)

        UrlUtils.init(sharedPrefManager)

        repository = ActivitiesRepositoryImpl(
            context,
            dispatcherProvider,
            lazyUserRepository,
            apiInterface,
            sharedPrefManager,
            timeProvider,
            userChallengeActionsDao,
            courseActivityDao,
            resourceActivityDao,
            offlineActivityDao,
            removedLogDao,
            searchActivityDao,
            userDao
        )
    }

    @After
    fun tearDown() {
        unmockkObject(NetworkUtils)
        unmockkStatic(Settings.Secure::class)
    }

    @Test
    fun `getOfflineVisitCount returns correct count`() = runTest {
        coEvery { offlineActivityDao.countByUserIdAndType("user1", UserSessionManager.KEY_LOGIN) } returns 5
        val result = repository.getOfflineVisitCount("user1")
        assertEquals(5, result)
    }

    @Test
    fun `getOfflineLoginCount returns correct count`() = runTest {
        coEvery { offlineActivityDao.countByUserNameAndType("john", UserSessionManager.KEY_LOGIN) } returns 3
        val result = repository.getOfflineLoginCount("john")
        assertEquals(3, result)
    }

    @Test
    fun `getOfflineLogins returns flow of activities`() = runTest {
        val mockActivities = listOf(OfflineActivity().apply { userName = "john" })
        every { offlineActivityDao.observeByUserNameAndType("john", UserSessionManager.KEY_LOGIN) } returns flowOf(mockActivities)

        repository.getOfflineLogins("john").collect {
            assertEquals(mockActivities, it)
        }
    }

    @Test
    fun `getOfflineLogins drops consecutive emissions with identical logins`() = runTest {
        val first = listOf(OfflineActivity().apply { id = "1"; userName = "john"; loginTime = 100L })
        val identical = listOf(OfflineActivity().apply { id = "1"; userName = "john"; loginTime = 100L })
        val changed = listOf(OfflineActivity().apply { id = "1"; userName = "john"; loginTime = 200L })
        every {
            offlineActivityDao.observeByUserNameAndType("john", UserSessionManager.KEY_LOGIN)
        } returns flowOf(first, identical, changed)

        val emissions = repository.getOfflineLogins("john").toList()

        assertEquals(2, emissions.size)
        assertEquals(listOf(100L), emissions[0].map { it.loginTime })
        assertEquals(listOf(200L), emissions[1].map { it.loginTime })
    }

    @Test
    fun `markResourceAdded removes from removedLogDao`() = runTest {
        repository.markResourceAdded("user1", "res1")
        coVerify { removedLogDao.deleteByTypeUserAndDoc("resources", "user1", "res1") }
    }

    @Test
    fun `markResourceRemoved inserts to removedLogDao`() = runTest {
        val slot = slot<RemovedLog>()
        repository.markResourceRemoved("user1", "res1")
        coVerify { removedLogDao.insert(capture(slot)) }
        assertEquals("res1", slot.captured.docId)
        assertEquals("user1", slot.captured.userId)
        assertEquals("resources", slot.captured.type)
    }

    @Test
    fun `logCourseVisit inserts course activity`() = runTest {
        val mockUser = UserEntity().apply {
            parentCode = "parent"
            planetCode = "planet"
        }
        coEvery { userDao.getByName("user1") } returns mockUser

        val slot = slot<CourseActivity>()
        repository.logCourseVisit("course1", "Course Title", "user1")

        coVerify { courseActivityDao.insert(capture(slot)) }
        assertEquals("course1", slot.captured.courseId)
        assertEquals("Course Title", slot.captured.title)
        assertEquals("user1", slot.captured.user)
        assertEquals("visit", slot.captured.type)
        assertEquals("parent", slot.captured.parentCode)
        assertEquals("planet", slot.captured.createdOn)
    }

    @Test
    fun `logLogin inserts offline activity`() = runTest {
        val slot = slot<OfflineActivity>()
        repository.logLogin("user1", "john", "parent", "planet")

        coVerify { offlineActivityDao.insert(capture(slot)) }
        assertEquals("user1", slot.captured.userId)
        assertEquals("john", slot.captured.userName)
        assertEquals("parent", slot.captured.parentCode)
        assertEquals("planet", slot.captured.createdOn)
        assertEquals(UserSessionManager.KEY_LOGIN, slot.captured.type)
        assertEquals("Member login on offline application", slot.captured.description)
    }

    @Test
    fun `logLogout updates logout time`() = runTest {
        val mockActivity = OfflineActivity().apply { id = "act1" }
        coEvery { offlineActivityDao.getLatestByType(UserSessionManager.KEY_LOGIN) } returns mockActivity

        repository.logLogout("john")

        coVerify { offlineActivityDao.updateLogoutTime(eq("act1"), any()) }
    }

    @Test
    fun `getGlobalLastVisit returns correct value`() = runTest {
        coEvery { offlineActivityDao.getGlobalLastVisit() } returns 1000L
        val result = repository.getGlobalLastVisit()
        assertEquals(1000L, result)
    }

    @Test
    fun `getLastVisit returns correct value`() = runTest {
        coEvery { offlineActivityDao.getLastVisit("john") } returns 2000L
        val result = repository.getLastVisit("john")
        assertEquals(2000L, result)
    }

    @Test
    fun `logResourceOpen inserts resource activity`() = runTest {
        val slot = slot<ResourceActivity>()
        repository.logResourceOpen("john", "parent", "planet", "Res Title", "res1", "pdf")

        coVerify { resourceActivityDao.insert(capture(slot)) }
        assertEquals("john", slot.captured.user)
        assertEquals("parent", slot.captured.parentCode)
        assertEquals("planet", slot.captured.createdOn)
        assertEquals("Res Title", slot.captured.title)
        assertEquals("res1", slot.captured.resourceId)
        assertEquals("pdf", slot.captured.type)
    }

    @Test
    fun `getResourceOpenCount returns correct count`() = runTest {
        coEvery { resourceActivityDao.countByUserAndType("john", "pdf") } returns 10L
        val result = repository.getResourceOpenCount("john", "pdf")
        assertEquals(10L, result)
    }

    @Test
    fun `getMostOpenedResource returns null when no activities`() = testScope.runTest {
        coEvery { resourceActivityDao.getByUserAndType("john", "pdf") } returns emptyList()
        val result = repository.getMostOpenedResource("john", "pdf")
        assertNull(result)
    }

    @Test
    fun `getMostOpenedResource returns correct pair`() = testScope.runTest {
        val activities = listOf(
            ResourceActivity().apply { resourceId = "res1"; title = "Res 1" },
            ResourceActivity().apply { resourceId = "res1"; title = "Res 1" },
            ResourceActivity().apply { resourceId = "res2"; title = "Res 2" }
        )
        coEvery { resourceActivityDao.getByUserAndType("john", "pdf") } returns activities

        val result = repository.getMostOpenedResource("john", "pdf")

        assertEquals("Res 1", result?.first)
        assertEquals(2, result?.second)
    }

    @Test
    fun `recordSyncUserChallengeAction inserts action`() = runTest {
        coEvery { timeProvider.now() } returns 5000L
        val slot = slot<UserChallengeActions>()

        repository.recordSyncUserChallengeAction("user1")

        coVerify { userChallengeActionsDao.insert(capture(slot)) }
        assertEquals("user1", slot.captured.userId)
        assertEquals("sync", slot.captured.actionType)
        assertNull(slot.captured.resourceId)
        assertEquals(5000L, slot.captured.time)
    }

    @Test
    fun `recordSyncActivity inserts resource activity`() = runTest {
        val mockUser = UserEntity().apply {
            id = "user1"
            name = "john"
            parentCode = "parent"
            planetCode = "planet"
        }
        coEvery { userRepository.getUserById("user1") } returns mockUser

        val slot = slot<ResourceActivity>()
        repository.recordSyncActivity("user1")

        coVerify { resourceActivityDao.insert(capture(slot)) }
        assertEquals("john", slot.captured.user)
        assertEquals("parent", slot.captured.parentCode)
        assertEquals("planet", slot.captured.createdOn)
        assertEquals("sync", slot.captured.type)
    }

    @Test
    fun `recordSyncActivity does nothing for guest users`() = runTest {
        val mockUser = UserEntity().apply { id = "guest123" }
        coEvery { userRepository.getUserById("guest123") } returns mockUser

        repository.recordSyncActivity("guest123")

        coVerify(exactly = 0) { resourceActivityDao.insert(any()) }
    }

    @Test
    fun `hasUserSyncAction returns true when sync completed`() = runTest {
        coEvery { userChallengeActionsDao.countByUserAndType("user1", "sync") } returns 1
        val result = repository.hasUserSyncAction("user1")
        assertTrue(result)
    }

    @Test
    fun `hasUserSyncAction returns false when userId is null`() = runTest {
        val result = repository.hasUserSyncAction(null)
        assertFalse(result)
    }

    @Test
    fun `hasUserCompletedSync returns false when userId is empty`() = runTest {
        val result = repository.hasUserCompletedSync("")
        assertFalse(result)
    }

    @Test
    fun `getPendingCourseActivityUploads returns correct list`() = runTest {
        val mockList = listOf(CourseActivity())
        coEvery { courseActivityDao.getPendingUploads() } returns mockList

        val result = repository.getPendingCourseActivityUploads()
        assertEquals(mockList, result)
    }

    @Test
    fun `markCourseActivityUploaded returns true on success`() = runTest {
        coEvery { courseActivityDao.markUploaded("local1", "remote1", "rev1") } returns 1
        val result = repository.markCourseActivityUploaded("local1", "remote1", "rev1")
        assertTrue(result)
    }

    @Test
    fun `markCourseActivityUploaded returns false on failure`() = runTest {
        coEvery { courseActivityDao.markUploaded("local1", "remote1", "rev1") } returns 0
        val result = repository.markCourseActivityUploaded("local1", "remote1", "rev1")
        assertFalse(result)
    }

    private fun loginDoc(
        id: String,
        loginTime: Long,
        userName: String,
        rev: String = "rev-$id"
    ): JsonObject = JsonObject().apply {
        addProperty("_id", id)
        addProperty("_rev", rev)
        addProperty("loginTime", loginTime)
        addProperty("user", userName)
        addProperty("type", "login")
    }

    @Test
    fun `insertLoginActivitiesFromSync collects lookup keys in a single pass`() = runTest {
        val docs = listOf(
            loginDoc("a1", 100L, "alice"),
            loginDoc("a2", 200L, "bob"),
            loginDoc("a1", 100L, "alice")
        )
        coEvery { offlineActivityDao.getByRemoteIds(listOf("a1", "a2")) } returns emptyList()
        coEvery {
            offlineActivityDao.getByLoginTimesAndUserNames(listOf(100L, 200L), listOf("alice", "bob"))
        } returns emptyList()

        repository.insertLoginActivitiesFromSync(docs)

        coVerify(exactly = 1) { offlineActivityDao.getByRemoteIds(listOf("a1", "a2")) }
        coVerify(exactly = 1) {
            offlineActivityDao.getByLoginTimesAndUserNames(listOf(100L, 200L), listOf("alice", "bob"))
        }
        val slot = slot<List<OfflineActivity>>()
        coVerify(exactly = 1) { offlineActivityDao.upsertAll(capture(slot)) }
        assertEquals(3, slot.captured.size)
    }

    @Test
    fun `insertLoginActivitiesFromSync dedupes ids loginTimes and userNames`() = runTest {
        val docs = listOf(
            loginDoc("a1", 100L, "alice"),
            loginDoc("a1", 100L, "alice")
        )
        coEvery { offlineActivityDao.getByRemoteIds(listOf("a1")) } returns emptyList()
        coEvery {
            offlineActivityDao.getByLoginTimesAndUserNames(listOf(100L), listOf("alice"))
        } returns emptyList()

        repository.insertLoginActivitiesFromSync(docs)

        coVerify(exactly = 1) { offlineActivityDao.getByRemoteIds(listOf("a1")) }
        coVerify(exactly = 1) {
            offlineActivityDao.getByLoginTimesAndUserNames(listOf(100L), listOf("alice"))
        }
        val slot = slot<List<OfflineActivity>>()
        coVerify(exactly = 1) { offlineActivityDao.upsertAll(capture(slot)) }
        assertEquals(2, slot.captured.size)
    }

    @Test
    fun `insertLoginActivitiesFromSync skips design docs and empty keys`() = runTest {
        val docs = listOf(
            JsonObject().apply { addProperty("_id", "_design/someview") },
            JsonObject().apply { addProperty("_id", "") },
            loginDoc("a1", 100L, "alice")
        )
        coEvery { offlineActivityDao.getByRemoteIds(listOf("a1")) } returns emptyList()
        coEvery {
            offlineActivityDao.getByLoginTimesAndUserNames(listOf(100L), listOf("alice"))
        } returns emptyList()

        repository.insertLoginActivitiesFromSync(docs)

        coVerify(exactly = 1) { offlineActivityDao.getByRemoteIds(listOf("a1")) }
        val slot = slot<List<OfflineActivity>>()
        coVerify(exactly = 1) { offlineActivityDao.upsertAll(capture(slot)) }
        assertEquals(2, slot.captured.size)
    }

    @Test
    fun `insertLoginActivitiesFromSync returns early when only design docs`() = runTest {
        val docs = listOf(JsonObject().apply { addProperty("_id", "_design/someview") })

        repository.insertLoginActivitiesFromSync(docs)

        coVerify(exactly = 0) { offlineActivityDao.getByRemoteIds(any()) }
        coVerify(exactly = 0) { offlineActivityDao.getByLoginTimesAndUserNames(any(), any()) }
        coVerify(exactly = 0) { offlineActivityDao.upsertAll(any()) }
    }

    @Test
    fun `uploadMyPlanetActivities posts activities and usage stats when existing doc found`() = testScope.runTest {
        val usageStatsManager = mockk<android.app.usage.UsageStatsManager>(relaxed = true)
        every { context.getSystemService(Context.USAGE_STATS_SERVICE) } returns usageStatsManager
        every { usageStatsManager.queryUsageStats(any(), any(), any()) } returns emptyList()

        val mockResponseBody = JsonObject().apply {
            add("usages", com.google.gson.JsonArray())
        }
        val mockResponse = mockk<retrofit2.Response<JsonObject>>()
        every { mockResponse.body() } returns mockResponseBody
        coEvery { apiInterface.getJsonObject(any(), any()) } returns mockResponse

        val userModel = UserEntity().apply {
            parentCode = "parent"
            planetCode = "planet"
        }

        repository.uploadMyPlanetActivities(userModel)

        coVerify(exactly = 2) { apiInterface.postDoc(any(), eq("application/json"), any(), any()) }
        coVerify(exactly = 1) { apiInterface.getJsonObject(any(), any()) }
    }

    @Test
    fun `uploadMyPlanetActivities posts fallback activities when no existing doc found`() = testScope.runTest {
        val usageStatsManager = mockk<android.app.usage.UsageStatsManager>(relaxed = true)
        every { context.getSystemService(Context.USAGE_STATS_SERVICE) } returns usageStatsManager
        every { usageStatsManager.queryUsageStats(any(), any(), any()) } returns emptyList()

        val mockResponse = mockk<retrofit2.Response<JsonObject>>()
        every { mockResponse.body() } returns null
        coEvery { apiInterface.getJsonObject(any(), any()) } returns mockResponse

        val userModel = UserEntity().apply {
            parentCode = "parent"
            planetCode = "planet"
        }

        repository.uploadMyPlanetActivities(userModel)

        coVerify(exactly = 2) { apiInterface.postDoc(any(), eq("application/json"), any(), any()) }
        coVerify(exactly = 1) { apiInterface.getJsonObject(any(), any()) }
    }

    @Test
    fun `insertLoginActivitiesFromSync skips fallback lookup when keys missing`() = runTest {
        val docs = listOf(
            JsonObject().apply {
                addProperty("_id", "a1")
                addProperty("_rev", "rev-a1")
            }
        )
        coEvery { offlineActivityDao.getByRemoteIds(listOf("a1")) } returns emptyList()

        repository.insertLoginActivitiesFromSync(docs)

        coVerify(exactly = 0) {
            offlineActivityDao.getByLoginTimesAndUserNames(any(), any())
        }
        val slot = slot<List<OfflineActivity>>()
        coVerify(exactly = 1) { offlineActivityDao.upsertAll(capture(slot)) }
        assertEquals(1, slot.captured.size)
    }
}
