package org.ole.planet.myplanet.di

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.EntryPointAccessors
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.NotificationUtils
import org.ole.planet.myplanet.utils.TimeProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ServiceModuleTest {

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

    @Test
    fun `provideNotificationManager creates notification manager instance`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mockTimeProvider = mockk<TimeProvider>(relaxed = true)

        val manager = ServiceModule.provideNotificationManager(context, mockTimeProvider)
        assertNotNull(manager)
    }

    @Test
    fun `getInstance delegates to CoreDependenciesEntryPoint`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mockManager = mockk<NotificationUtils.NotificationManager>()
        val mockEntryPoint = mockk<CoreDependenciesEntryPoint>()
        every { mockEntryPoint.notificationManager() } returns mockManager

        mockkStatic(EntryPointAccessors::class)
        every {
            EntryPointAccessors.fromApplication(any(), CoreDependenciesEntryPoint::class.java)
        } returns mockEntryPoint

        val instance = NotificationUtils.getInstance(context)

        assertSame(mockManager, instance)
        verify { mockEntryPoint.notificationManager() }
    }
}
