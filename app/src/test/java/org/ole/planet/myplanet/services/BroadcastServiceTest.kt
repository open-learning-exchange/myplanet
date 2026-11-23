package org.ole.planet.myplanet.services

import android.content.Intent
import io.mockk.EqMatcher
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.utils.Constants

@OptIn(ExperimentalCoroutinesApi::class)
class BroadcastServiceTest {

    private lateinit var broadcastService: BroadcastService

    @Before
    fun setUp() {
        broadcastService = BroadcastService()
    }

    @After
    fun tearDown() {
        unmockkConstructor(Intent::class)
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
    fun `trySendRetryEvent builds retry intent with action and extras`() {
        mockkConstructor(Intent::class)
        every { constructedWith<Intent>(EqMatcher(Constants.ACTION_RETRY_EVENT)).putExtra(any(), any<String>()) } returns mockk<Intent>()
        every { constructedWith<Intent>(EqMatcher(Constants.ACTION_RETRY_EVENT)).putExtra(any(), any<Int>()) } returns mockk<Intent>()
        every { constructedWith<Intent>(EqMatcher(Constants.ACTION_RETRY_EVENT)).putExtra(any(), any<Long>()) } returns mockk<Intent>()

        val result = broadcastService.trySendRetryEvent("/db/_find", 1, 10L)

        assertTrue(result)
        verify(exactly = 1) { constructedWith<Intent>(EqMatcher(Constants.ACTION_RETRY_EVENT)).putExtra("url", "/db/_find") }
        verify(exactly = 1) { constructedWith<Intent>(EqMatcher(Constants.ACTION_RETRY_EVENT)).putExtra("attempt", 1) }
        verify(exactly = 1) { constructedWith<Intent>(EqMatcher(Constants.ACTION_RETRY_EVENT)).putExtra("delay", 10L) }
    }
}
