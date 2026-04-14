package org.ole.planet.myplanet.di

import io.mockk.every
import io.mockk.mockk
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
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
}
