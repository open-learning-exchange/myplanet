package org.ole.planet.myplanet.services.sync

import android.content.Context
import android.content.SharedPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.NotificationUtils
import org.ole.planet.myplanet.utils.SyncTimeLogger

@OptIn(ExperimentalCoroutinesApi::class)

class ImprovedSyncManagerTest {

    private lateinit var syncManager: ImprovedSyncManager
    private val context: Context = mockk(relaxed = true)
    private val databaseService: DatabaseService = mockk(relaxed = true)
    private val settings: SharedPreferences = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)
    private val transactionSyncManager: TransactionSyncManager = mockk(relaxed = true)
    private val standardStrategy: StandardSyncStrategy = mockk(relaxed = true)
    private val loginSyncManager: LoginSyncManager = mockk(relaxed = true)
    private val activitiesRepository: ActivitiesRepository = mockk(relaxed = true)
    private val dispatcherProvider: DispatcherProvider = mockk(relaxed = true)

    private val poolManager: RealmPoolManager = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        mockkObject(RealmPoolManager.Companion)
        every { RealmPoolManager.getInstance() } returns poolManager

        mockkStatic(NotificationUtils::class)
        every { NotificationUtils.create(any(), any(), any(), any()) } returns Unit
        every { NotificationUtils.cancel(any(), any()) } returns Unit

        mockkObject(SyncTimeLogger)
        every { SyncTimeLogger.startLogging() } returns Unit
        every { SyncTimeLogger.stopLogging() } returns Unit
        every { SyncTimeLogger.startProcess(any()) } returns Unit
        every { SyncTimeLogger.endProcess(any()) } returns Unit

        mockkObject(MainApplication.Companion)
        every { MainApplication.createLog(any(), any()) } returns Unit

        every { dispatcherProvider.io } returns testDispatcher
        every { dispatcherProvider.main } returns testDispatcher
        every { dispatcherProvider.default } returns testDispatcher

        every { settings.getString("userId", "") } returns "test_user_id"

        mockkConstructor(AdaptiveBatchProcessor::class)
        every { anyConstructed<AdaptiveBatchProcessor>().getOptimalConfig(any()) } returns SyncConfig()
        syncManager = ImprovedSyncManager(
            context,
            databaseService,
            settings,
            sharedPrefManager,
            transactionSyncManager,
            standardStrategy,
            loginSyncManager,
            activitiesRepository,
            dispatcherProvider
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testInitialize() = runTest(testDispatcher) {
        coEvery { poolManager.initializePool(any(), any(), any()) } returns Unit

        syncManager.initialize()
        advanceUntilIdle()

        coVerify { poolManager.initializePool(context, databaseService, any()) }
    }

    @Test
    fun testStart_syncNotRunning_startsSyncAndAuthenticates() = runTest(testDispatcher) {
        val listener: OnSyncListener = mockk(relaxed = true)

        coEvery { transactionSyncManager.authenticate() } returns true
        every { standardStrategy.isSupported(any()) } returns true
        coEvery { standardStrategy.syncTable(any(), any()) } returns flowOf(mockk())
        coEvery { loginSyncManager.syncAdmin() } returns Unit
        coEvery { activitiesRepository.recordSyncActivity(any()) } returns Unit

        syncManager.start(listener, SyncMode.Standard, listOf("test_table"))
        advanceUntilIdle()

        coVerify { listener.onSyncStarted() }
        coVerify { MainApplication.createLog("improved_sync_start", any()) }
        coVerify { transactionSyncManager.authenticate() }
        coVerify { standardStrategy.isSupported("test_table") }
        coVerify { standardStrategy.syncTable("test_table", any()) }
        coVerify { loginSyncManager.syncAdmin() }
        coVerify { activitiesRepository.recordSyncActivity("test_user_id") }
        coVerify { listener.onSyncComplete() }
    }

    @Test
    fun testStart_authFails() = runTest(testDispatcher) {
        val listener: OnSyncListener = mockk(relaxed = true)

        coEvery { transactionSyncManager.authenticate() } returns false

        syncManager.start(listener, SyncMode.Standard, listOf("test_table"))
        advanceUntilIdle()

        coVerify { listener.onSyncStarted() }
        coVerify { transactionSyncManager.authenticate() }
        coVerify { listener.onSyncFailed("Authentication failed") }
        coVerify(exactly = 0) { standardStrategy.syncTable(any(), any()) }
        coVerify { listener.onSyncComplete() }
    }

    @Test
    fun testStart_exception() = runTest(testDispatcher) {
        val listener: OnSyncListener = mockk(relaxed = true)

        coEvery { transactionSyncManager.authenticate() } throws RuntimeException("Network error")

        syncManager.start(listener, SyncMode.Standard, listOf("test_table"))
        advanceUntilIdle()

        coVerify { listener.onSyncStarted() }
        coVerify { transactionSyncManager.authenticate() }
        coVerify { listener.onSyncFailed("Network error") }
        coVerify(exactly = 0) { standardStrategy.syncTable(any(), any()) }
        coVerify { listener.onSyncComplete() }
    }
}
