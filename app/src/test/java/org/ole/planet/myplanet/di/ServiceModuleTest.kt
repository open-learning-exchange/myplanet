package org.ole.planet.myplanet.di

import io.mockk.every
import io.mockk.mockk
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CoroutineScope
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.utils.DispatcherProvider

class ServiceModuleTest {

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `provideApplicationScope returns scope with io dispatcher`() {
        // Arrange
        mockkObject(MainApplication)
        val mockScope = mockk<CoroutineScope>()
        every { mockScope.coroutineContext } returns Dispatchers.Unconfined
        every { MainApplication.applicationScope } returns mockScope

        val mockDispatcherProvider = mockk<DispatcherProvider>()
        val expectedDispatcher = Dispatchers.IO
        every { mockDispatcherProvider.io } returns expectedDispatcher

        // Act
        val scope = ServiceModule.provideApplicationScope(mockDispatcherProvider)

        // Assert
        val actualDispatcher = scope.coroutineContext[ContinuationInterceptor]
        assertEquals(expectedDispatcher, actualDispatcher)
    }
}
