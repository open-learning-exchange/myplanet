package org.ole.planet.myplanet.services

import android.content.Intent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BroadcastServiceTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

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
}
