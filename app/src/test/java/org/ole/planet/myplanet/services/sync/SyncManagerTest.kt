package org.ole.planet.myplanet.services.sync

import android.content.Context
import android.net.ConnectivityManager
import android.os.SystemClock
import android.util.Log
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.model.DocumentResponse
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.SyncRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserSyncRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.NotificationUtils
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.TestTimeProvider
import org.ole.planet.myplanet.utils.UrlUtils
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class SyncManagerTest {

    private lateinit var syncManager: SyncManager
    private val context: Context = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)
    private val apiInterface: ApiInterface = mockk(relaxed = true)
    private val transactionSyncManager: TransactionSyncManager = mockk(relaxed = true)
    private val resourcesRepository: ResourcesRepository = mockk(relaxed = true)
    private val loginSyncManager: LoginSyncManager = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val activitiesRepository: ActivitiesRepository = mockk(relaxed = true)
    private val dispatcherProvider: DispatcherProvider = TestDispatcherProvider(testDispatcher)
    private val teamsRepository: TeamsRepository = mockk(relaxed = true)
    private val listener: OnSyncListener = mockk(relaxed = true)

    @Before
    fun setup() {
        mockkObject(MainApplication.Companion)
        every { MainApplication.createLog(any(), any()) } returns Unit

        syncManager = SyncManager(
            context,
            sharedPrefManager,
            apiInterface,
            transactionSyncManager,
            resourcesRepository,
            loginSyncManager,
            testScope,
            activitiesRepository,
            dispatcherProvider,
            TestTimeProvider(),
            teamsRepository,
            mockk(),
            mockk(),
            mockk(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `resetSyncStatus sets status to Idle`() = runTest {
        // First change it to something else to ensure it actually resets
        syncManager.start(listener, "sync", listOf())
        syncManager.resetSyncStatus()
        assertEquals(SyncManager.SyncStatus.Idle, syncManager.syncStatus.value)
    }

    @Test
    fun `start authenticates and reports failure when authentication fails`() = runTest {
        every { context.getString(org.ole.planet.myplanet.R.string.invalid_configuration) } returns "Invalid configuration"
        coEvery { transactionSyncManager.authenticate() } returns false

        syncManager.start(listener, "sync", listOf("exams"))

        verify { listener.onSyncStarted() }
        coVerify { transactionSyncManager.authenticate() }
        verify { listener.onSyncFailed("Invalid configuration") }
    }

    @Test
    fun `cancelBackgroundSync clears background sync and listener`() = runTest {
        // Suspend in authenticate so the background sync stays in-flight until we cancel it
        coEvery { transactionSyncManager.authenticate() } coAnswers { awaitCancellation() }

        syncManager.start(listener, "sync", listOf())
        syncManager.cancelBackgroundSync()

        verify(exactly = 0) { listener.onSyncComplete() }
        verify(exactly = 0) { listener.onSyncFailed(any()) }
    }

    @Test
    fun `parallel Phase 1 table completions coalesce into far fewer status emissions`() = runTest {
        // Drive a full sync through the 4 phases with the network/DB layers stubbed to no-ops,
        // counting every Phase 1 Syncing *write* (not collector reads, which the StateFlow conflates).
        mockkObject(UrlUtils)
        every { UrlUtils.getUrl() } returns "http://mockurl"
        every { UrlUtils.header } returns "Basic mock"
        mockkObject(NotificationUtils)
        every { NotificationUtils.create(any(), any(), any(), any()) } returns Unit
        every { NotificationUtils.cancel(any(), any()) } returns Unit
        mockkObject(HeavyTableSyncWorker)
        every { HeavyTableSyncWorker.schedule(any(), any()) } returns Unit

        coEvery { transactionSyncManager.authenticate() } returns true
        // Each table completes immediately, so all ~14 finish in a burst.
        coEvery { transactionSyncManager.syncDb(any()) } returns 0
        coEvery { transactionSyncManager.syncNotificationReads() } returns Unit
        every { loginSyncManager.syncAdmin() } returns Unit

        // Android framework stubs throw "not mocked" unless stubbed; neutralize the ones startFullSync hits.
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 0L
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0

        // initializeSync() reads the connectivity service; stub it so it doesn't throw.
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { connectivityManager.activeNetwork } returns null
        every { connectivityManager.getNetworkCapabilities(null) } returns null

        // Resources phase: empty doc body -> totalRows 0 -> single no-op iteration, no retries.
        val emptyJsonResponse = mockk<Response<JsonObject>>(relaxed = true)
        every { emptyJsonResponse.isSuccessful } returns true
        every { emptyJsonResponse.body() } returns JsonObject()
        coEvery { apiInterface.getJsonObject(any(), any()) } returns emptyJsonResponse

        // Library phase: no shelves with data -> library sync is skipped.
        val emptyDocResponse = mockk<Response<DocumentResponse>>(relaxed = true)
        every { emptyDocResponse.isSuccessful } returns true
        every { emptyDocResponse.body() } returns DocumentResponse().apply { rows = emptyList() }
        coEvery { apiInterface.getDocuments(any(), any()) } returns emptyDocResponse

        val phase1Writes = mutableListOf<Int>()
        val observingManager = object : SyncManager(
            context, sharedPrefManager, apiInterface, transactionSyncManager, resourcesRepository,
            loginSyncManager, testScope, activitiesRepository, dispatcherProvider, TestTimeProvider(),
            teamsRepository, mockk(), mockk(), mockk(relaxed = true)
        ) {
            override fun emitSyncStatus(status: SyncStatus.Syncing) {
                if (status.phaseIndex == 1) phase1Writes += status.itemsDone
                super.emitSyncStatus(status)
            }
        }

        observingManager.start(listener, "sync", listOf())
        testScope.advanceUntilIdle()

        // 14 tables complete; without coalescing each would push its own write (~15 emissions).
        // Coalescing collapses the burst into a handful (initial 0-count + final flush).
        assertTrue(
            "expected far fewer than 14 Phase 1 writes, got ${phase1Writes.size}",
            phase1Writes.size <= 3,
        )
        // The terminal value is always emitted, so the UI still sees 14/14.
        assertEquals(14, phase1Writes.last())
    }
}
