package org.ole.planet.myplanet.di

import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.utils.DispatcherProvider

class ServiceModuleTest {

    @Before
    fun setUp() {
        // android.util.Log is an unmocked stub in plain JVM tests, and the scope's handler logs
        // through it, so leaving it unstubbed turns a handled failure back into a thrown one.
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `provideApplicationScope uses dispatcherProvider io dispatcher`() {
        // Arrange
        val mockDispatcherProvider = mockk<DispatcherProvider>()
        val expectedDispatcher = Dispatchers.Unconfined
        every { mockDispatcherProvider.io } returns expectedDispatcher

        // Act
        val scope = ServiceModule.provideApplicationScope(mockDispatcherProvider)

        // Assert
        val actualDispatcher = scope.coroutineContext[ContinuationInterceptor]
        assertEquals(expectedDispatcher, actualDispatcher)
    }

    @Test
    fun `provideApplicationScope installs an exception handler`() {
        val mockDispatcherProvider = mockk<DispatcherProvider>()
        every { mockDispatcherProvider.io } returns Dispatchers.Unconfined

        val scope = ServiceModule.provideApplicationScope(mockDispatcherProvider)

        assertNotNull(scope.coroutineContext[CoroutineExceptionHandler])
    }

    @Test
    fun `failures in application scope do not escape to the uncaught handler`() = runTest {
        // Without a handler in the scope's context the throwable reaches the platform uncaught
        // handler — which crashes the app in production, and in tests lands in the coroutines
        // test exception collector and fails this test (or the next one in the same JVM fork).
        val mockDispatcherProvider = mockk<DispatcherProvider>()
        every { mockDispatcherProvider.io } returns StandardTestDispatcher(testScheduler)

        val scope = ServiceModule.provideApplicationScope(mockDispatcherProvider)
        val job = scope.launch { throw IllegalStateException("boom") }
        job.join()

        assertTrue(job.isCancelled)
    }
}
