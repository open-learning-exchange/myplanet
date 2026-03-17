package org.ole.planet.myplanet.services

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.work.WorkerParameters
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.coVerify
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.NetworkUtils

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class StayOnlineWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var broadcastService: BroadcastService
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var dispatcherProvider: DispatcherProvider
    private lateinit var stayOnlineWorker: StayOnlineWorker
    private lateinit var sharedPreferences: SharedPreferences

    @Before
    fun setup() {
        context = mockk<Context>(relaxed = true)
        workerParams = mockk(relaxed = true)
        broadcastService = mockk(relaxed = true)
        sharedPreferences = mockk<SharedPreferences>(relaxed = true)

        mockkStatic(PreferenceManager::class)
        every { PreferenceManager.getDefaultSharedPreferences(context) } returns sharedPreferences

        testDispatcher = StandardTestDispatcher()
        dispatcherProvider = object : DispatcherProvider {
            override val main: CoroutineDispatcher = testDispatcher
            override val io: CoroutineDispatcher = testDispatcher
            override val default: CoroutineDispatcher = testDispatcher
            override val unconfined: CoroutineDispatcher = testDispatcher
        }

        mockkObject(Constants)
        mockkObject(NetworkUtils)

        stayOnlineWorker = StayOnlineWorker(
            context,
            workerParams,
            broadcastService,
            dispatcherProvider
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `doWork should send broadcast when wifi feature is enabled and wifi is connected`() = runTest(testDispatcher) {
        // Arrange
        every { sharedPreferences.getBoolean("beta_function", false) } returns true
        every { sharedPreferences.getBoolean(Constants.KEY_SYNC, false) } returns true
        every { NetworkUtils.isWifiConnected() } returns true

        val intentSlot = slot<Intent>()
        coEvery { broadcastService.sendBroadcast(capture(intentSlot)) } returns Unit

        // Act
        val result = stayOnlineWorker.doWork()

        // Assert
        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        coVerify(exactly = 1) { broadcastService.sendBroadcast(any()) }
        assertEquals("SHOW_WIFI_ALERT", intentSlot.captured.action)
    }

    @Test
    fun `doWork should not send broadcast when wifi feature is disabled`() = runTest(testDispatcher) {
        // Arrange
        every { sharedPreferences.getBoolean("beta_function", false) } returns false
        every { sharedPreferences.getBoolean(Constants.KEY_SYNC, false) } returns false
        every { NetworkUtils.isWifiConnected() } returns true

        // Act
        val result = stayOnlineWorker.doWork()

        // Assert
        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        coVerify(exactly = 0) { broadcastService.sendBroadcast(any()) }
    }

    @Test
    fun `doWork should not send broadcast when wifi is disconnected`() = runTest(testDispatcher) {
        // Arrange
        every { sharedPreferences.getBoolean("beta_function", false) } returns true
        every { sharedPreferences.getBoolean(Constants.KEY_SYNC, false) } returns true
        every { NetworkUtils.isWifiConnected() } returns false

        // Act
        val result = stayOnlineWorker.doWork()

        // Assert
        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        coVerify(exactly = 0) { broadcastService.sendBroadcast(any()) }
    }
}
