package org.ole.planet.myplanet.di

import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.ole.planet.myplanet.utils.DispatcherProvider

class ServiceModuleTest {

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
    fun `provideApplicationScope logs failures instead of reaching the uncaught handler`() = runTest {
        val mockDispatcherProvider = mockk<DispatcherProvider>()
        every { mockDispatcherProvider.io } returns Dispatchers.Unconfined
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0

        try {
            val scope = ServiceModule.provideApplicationScope(mockDispatcherProvider)

            assertNotNull(scope.coroutineContext[CoroutineExceptionHandler])
            val failure = IllegalStateException("background work failed")
            scope.launch { throw failure }.join()

            verify { Log.e(any(), any(), failure) }
        } finally {
            unmockkStatic(Log::class)
        }
    }
}
