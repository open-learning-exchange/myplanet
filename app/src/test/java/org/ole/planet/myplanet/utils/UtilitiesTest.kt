package org.ole.planet.myplanet.utils

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class UtilitiesTest {

    private val mockContext = mockk<Context>(relaxed = true)

    @Before
    fun setup() {
        mockkStatic(Looper::class)
        mockkStatic(Toast::class)
        val mockLooper = mockk<Looper>()
        every { Looper.getMainLooper() } returns mockLooper
        val currentThread = Thread.currentThread()
        every { mockLooper.thread } returns currentThread
        every { mockLooper.getThread() } returns currentThread

        every { Looper.myLooper() } returns mockk<Looper>() // Different from main looper
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `toast dispatches via Handler when not on main thread`() {
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

        mockkConstructor(Handler::class)
        every { anyConstructed<Handler>().post(any()) } answers {
            firstArg<Runnable>().run()
            true
        }

        Utilities.toast(mockActivity, "test message", Toast.LENGTH_SHORT)

        verify(exactly = 1) { Toast.makeText(any(), "test message", Toast.LENGTH_SHORT) }
        verify(exactly = 1) { mockToast.show() }
        verify(exactly = 1) { anyConstructed<Handler>().post(any()) }
    }
}
