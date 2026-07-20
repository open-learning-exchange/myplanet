package org.ole.planet.myplanet.services

import android.app.Application
import android.content.Intent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class BroadcastServiceTest {

    private lateinit var broadcastService: BroadcastService

    @Before
    fun setUp() {
        broadcastService = BroadcastService()
    }

    @Test
    fun `sendBroadcast emits intent`() = runTest {
        val testIntent = Intent("TEST_ACTION")
        var receivedIntent: Intent? = null

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            receivedIntent = broadcastService.events.first()
        }

        broadcastService.sendBroadcast(testIntent)

        job.join()
        assertEquals(testIntent, receivedIntent)
    }

    @Test
    fun `trySendBroadcast emits intent and returns true`() = runTest {
        val testIntent = Intent("TEST_ACTION")
        var receivedIntent: Intent? = null

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            receivedIntent = broadcastService.events.first()
        }

        val result = broadcastService.trySendBroadcast(testIntent)

        job.join()
        assertTrue(result)
        assertEquals(testIntent, receivedIntent)
    }

    @Test
    fun `latestDownloadProgress starts null`() {
        assertNull(broadcastService.latestDownloadProgress.value)
    }

    @Test
    fun `sendBroadcast routes MESSAGE_PROGRESS to latestDownloadProgress instead of events`() = runTest {
        val progressIntent = Intent(DownloadService.MESSAGE_PROGRESS)
        var receivedOnEvents = false

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            broadcastService.events.collect { receivedOnEvents = true }
        }

        broadcastService.sendBroadcast(progressIntent)

        assertEquals(progressIntent, broadcastService.latestDownloadProgress.value)
        assertTrue("MESSAGE_PROGRESS must not be re-emitted on the one-shot `events` flow", !receivedOnEvents)
        job.cancel()
    }

    @Test
    fun `trySendBroadcast routes MESSAGE_PROGRESS to latestDownloadProgress and returns true`() {
        val progressIntent = Intent(DownloadService.MESSAGE_PROGRESS)

        val result = broadcastService.trySendBroadcast(progressIntent)

        assertTrue(result)
        assertEquals(progressIntent, broadcastService.latestDownloadProgress.value)
    }

    @Test
    fun `latestDownloadProgress replays the last value to a subscriber that attaches after the emission`() = runTest {
        val progressIntent = Intent(DownloadService.MESSAGE_PROGRESS).apply {
            putExtra("marker", "batch-complete")
        }

        // Emitted while nothing is subscribed - simulates a download finishing while the
        // consuming fragment's view was torn down (rotation) or the app was backgrounded.
        broadcastService.sendBroadcast(progressIntent)

        // A collector attaching afterwards must still observe it, unlike a replay = 0 SharedFlow.
        val received = broadcastService.latestDownloadProgress.first { it != null }

        assertEquals("batch-complete", received?.getStringExtra("marker"))
    }
}
