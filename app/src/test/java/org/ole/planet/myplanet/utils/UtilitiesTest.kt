package org.ole.planet.myplanet.utils

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import android.app.Activity
import android.os.Looper
import android.widget.Toast
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UtilitiesTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockContext = mockk<Context>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Looper::class)
        mockkStatic(Toast::class)
        val mockLooper = mockk<Looper>()
        every { Looper.getMainLooper() } returns mockLooper
        val currentThread = Thread.currentThread()
        every { mockLooper.thread } returns currentThread
        every { mockLooper.getThread() } returns currentThread

        every { Looper.myLooper() } returns mockk<Looper>()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `toast dispatches via mainDispatcher when not on main thread`() = runTest(testDispatcher) {
        io.mockk.mockkObject(ProcessLifecycleOwner.Companion)
        val mockLifecycleOwner = mockk<LifecycleOwner>()
        val mockLifecycleRegistry = LifecycleRegistry(mockLifecycleOwner)
        mockLifecycleRegistry.currentState = Lifecycle.State.RESUMED
        every { ProcessLifecycleOwner.get() } returns mockLifecycleOwner
        every { mockLifecycleOwner.lifecycle } returns mockLifecycleRegistry

        val mockActivity = mockk<Activity>(relaxed = true)
        every { mockActivity.isFinishing } returns false
        every { mockActivity.isDestroyed } returns false

        val mockToast = mockk<Toast>(relaxed = true)
        every { Toast.makeText(any(), any<CharSequence>(), any()) } returns mockToast

        Utilities.toast(mockActivity, "test message", Toast.LENGTH_SHORT, testDispatcher)

        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { Toast.makeText(any(), "test message", Toast.LENGTH_SHORT) }
        verify(exactly = 1) { mockToast.show() }
    }
}
