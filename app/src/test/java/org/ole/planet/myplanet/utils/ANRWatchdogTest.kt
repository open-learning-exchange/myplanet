package org.ole.planet.myplanet.utils

import android.os.Looper
import android.os.SystemClock
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ANRWatchdogTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)
    private val listener = mockk<ANRWatchdog.ANRListener>(relaxed = true)

    private var clock = 1000L
    private lateinit var watchdog: ANRWatchdog

    @Before
    fun setup() {
        clock = 1000L
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } answers { clock }
    }

    @After
    fun tearDown() {
        if (::watchdog.isInitialized) {
            watchdog.stop()
        }
        unmockkStatic(SystemClock::class)
    }

    private fun advanceTime(ms: Long) {
        clock += ms
        testDispatcher.scheduler.advanceTimeBy(ms)
    }

    @Test
    fun `blocked main thread triggers ANR report at exact detection window and reports correct duration`() = runTest(testDispatcher) {
        watchdog = ANRWatchdog(
            scope = backgroundScope,
            timeout = 1000L,
            listener = listener,
            dispatcherProvider = dispatcherProvider
        )

        watchdog.start()
        runCurrent()

        advanceTime(499L)
        runCurrent()
        verify(exactly = 0) { listener.onAppNotResponding(any(), any(), any()) }

        advanceTime(1L)
        runCurrent()

        verify(exactly = 1) {
            listener.onAppNotResponding(any(), any(), 500L)
        }
    }

    @Test
    fun `responsive main thread never triggers ANR report across multiple windows`() = runTest(testDispatcher) {
        watchdog = ANRWatchdog(
            scope = backgroundScope,
            timeout = 1000L,
            listener = listener,
            dispatcherProvider = dispatcherProvider
        )

        watchdog.start()
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()

        repeat(5) {
            advanceTime(500L)
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
        }

        verify(exactly = 0) { listener.onAppNotResponding(any(), any(), any()) }
    }

    @Test
    fun `back-off timing delays second report by full timeout plus detection window`() = runTest(testDispatcher) {
        watchdog = ANRWatchdog(
            scope = backgroundScope,
            timeout = 1000L,
            listener = listener,
            dispatcherProvider = dispatcherProvider
        )

        watchdog.start()
        runCurrent()

        advanceTime(500L)
        runCurrent()
        verify(exactly = 1) { listener.onAppNotResponding(any(), any(), any()) }

        advanceTime(1499L)
        runCurrent()
        verify(exactly = 1) { listener.onAppNotResponding(any(), any(), any()) }

        advanceTime(1L)
        runCurrent()
        verify(exactly = 2) { listener.onAppNotResponding(any(), any(), any()) }
    }

    @Test
    fun `stop prevents further ANR reports`() = runTest(testDispatcher) {
        watchdog = ANRWatchdog(
            scope = backgroundScope,
            timeout = 1000L,
            listener = listener,
            dispatcherProvider = dispatcherProvider
        )

        watchdog.start()
        runCurrent()

        watchdog.stop()
        runCurrent()

        advanceTime(2000L)
        runCurrent()

        verify(exactly = 0) { listener.onAppNotResponding(any(), any(), any()) }
    }
}
