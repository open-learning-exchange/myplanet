package org.ole.planet.myplanet.di

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.EntryPointAccessors
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.utils.NotificationUtils
import org.ole.planet.myplanet.utils.TimeProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NotificationModuleTest {

    private lateinit var context: Context
    private lateinit var mockTimeProvider: TimeProvider

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockTimeProvider = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `provideNotificationManager returns a non-null manager`() {
        val manager = NotificationModule.provideNotificationManager(context, mockTimeProvider)
        assertNotNull(manager)
    }

    @Test
    fun `getInstance returns the entry point instance`() {
        val mockManager = mockk<NotificationUtils.NotificationManager>()
        val mockEntryPoint = mockk<NotificationEntryPoint>()
        every { mockEntryPoint.notificationManager() } returns mockManager

        mockkStatic(EntryPointAccessors::class)
        every {
            EntryPointAccessors.fromApplication(any(), NotificationEntryPoint::class.java)
        } returns mockEntryPoint

        val instance1 = NotificationUtils.getInstance(context)
        val instance2 = NotificationUtils.getInstance(context)

        assertNotNull(instance1)
        assertSame(mockManager, instance1)
        assertSame(instance1, instance2)
    }
}
