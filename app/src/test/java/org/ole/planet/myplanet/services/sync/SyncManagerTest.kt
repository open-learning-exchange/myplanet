package org.ole.planet.myplanet.services.sync

import android.content.Context
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.EventsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class SyncManagerTest {

    private lateinit var syncManager: SyncManager
    private val context: Context = mockk(relaxed = true)
    private val databaseService: DatabaseService = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)
    private val apiInterface: ApiInterface = mockk(relaxed = true)
    private val improvedSyncManager: ImprovedSyncManager = mockk(relaxed = true)
    private val lazyImprovedSyncManager: Lazy<ImprovedSyncManager> = mockk(relaxed = true)
    private val transactionSyncManager: TransactionSyncManager = mockk(relaxed = true)
    private val resourcesRepository: ResourcesRepository = mockk(relaxed = true)
    private val loginSyncManager: LoginSyncManager = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val activitiesRepository: ActivitiesRepository = mockk(relaxed = true)
    private val dispatcherProvider: DispatcherProvider = TestDispatcherProvider(testDispatcher)
    private val teamsRepository: TeamsRepository = mockk(relaxed = true)
    private val teamsSyncRepository: org.ole.planet.myplanet.repository.TeamsSyncRepository = mockk(relaxed = true)
    private val coursesRepository: CoursesRepository = mockk(relaxed = true)
    private val eventsRepository: EventsRepository = mockk(relaxed = true)
    private val listener: OnSyncListener = mockk(relaxed = true)

    @Before
    fun setup() {
        mockkObject(MainApplication.Companion)
        every { MainApplication.createLog(any(), any()) } returns Unit

        every { lazyImprovedSyncManager.get() } returns improvedSyncManager

        syncManager = SyncManager(
            context,
            sharedPrefManager,
            apiInterface,
            lazyImprovedSyncManager,
            transactionSyncManager,
            resourcesRepository,
            loginSyncManager,
            testScope,
            activitiesRepository,
            dispatcherProvider,
            teamsRepository,
            teamsSyncRepository,
            coursesRepository,
            eventsRepository
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
    fun `start with useImprovedSync=true and type=sync uses improved sync manager`() = runTest {
        every { sharedPrefManager.getUseImprovedSync() } returns true
        every { sharedPrefManager.getFastSync() } returns true

        syncManager.start(listener, "sync", listOf("exams"))

        verify { listener.onSyncStarted() }
        coVerify { improvedSyncManager.initialize() }
        verify { improvedSyncManager.start(any(), SyncMode.Fast, listOf("exams")) }
    }

    @Test
    fun `start with useImprovedSync=false uses legacy sync manager`() = runTest {
        every { context.getString(org.ole.planet.myplanet.R.string.invalid_configuration) } returns "Invalid configuration"
        every { sharedPrefManager.getUseImprovedSync() } returns false
        coEvery { transactionSyncManager.authenticate() } returns false

        syncManager.start(listener, "sync", listOf("exams"))

        verify { listener.onSyncStarted() }
        coVerify { transactionSyncManager.authenticate() }
        verify { listener.onSyncFailed("Invalid configuration") }
    }

    @Test
    fun `cancelBackgroundSync clears background sync and listener`() = runTest {
        // Prevent immediate execution of background sync logic so we can cancel it
        every { sharedPrefManager.getUseImprovedSync() } returns true
        every { sharedPrefManager.getFastSync() } returns true

        syncManager.start(listener, "sync", listOf())
        syncManager.cancelBackgroundSync()

        verify(exactly = 0) { listener.onSyncComplete() }
        verify(exactly = 0) { listener.onSyncFailed(any()) }
    }
}
