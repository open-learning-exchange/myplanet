package org.ole.planet.myplanet.services

import android.content.Context
import android.content.Intent
import androidx.work.WorkerParameters
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
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
import androidx.test.core.app.ApplicationProvider

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StayOnlineWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var broadcastService: BroadcastService
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var dispatcherProvider: DispatcherProvider
    private lateinit var stayOnlineWorker: StayOnlineWorker

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        workerParams = mockk(relaxed = true)
        broadcastService = mockk(relaxed = true)

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
        every { Constants.isBetaWifiFeatureEnabled(any()) } returns true
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
        every { Constants.isBetaWifiFeatureEnabled(any()) } returns false
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
        every { Constants.isBetaWifiFeatureEnabled(any()) } returns true
        every { NetworkUtils.isWifiConnected() } returns false

        // Act
        val result = stayOnlineWorker.doWork()

        // Assert
        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        coVerify(exactly = 0) { broadcastService.sendBroadcast(any()) }
    }
}
