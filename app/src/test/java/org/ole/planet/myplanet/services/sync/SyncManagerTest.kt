package org.ole.planet.myplanet.services.sync

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
import org.ole.planet.myplanet.repository.TeamsSyncRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.TestTimeProvider

@OptIn(ExperimentalCoroutinesApi::class)
class SyncManagerTest {

    private lateinit var syncManager: SyncManager
    private val context: Context = mockk(relaxed = true)
    private val databaseService: DatabaseService = mockk(relaxed = true)
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
    private val teamsSyncRepository: TeamsSyncRepository = mockk(relaxed = true)
    private val coursesRepository: CoursesRepository = mockk(relaxed = true)
    private val eventsRepository: EventsRepository = mockk(relaxed = true)
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
}
