package org.ole.planet.myplanet.services

import android.content.Context
import android.content.Intent
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.verify
import android.content.BroadcastReceiver
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.di.getBroadcastService
import org.ole.planet.myplanet.repository.NotificationsRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.NotificationUtils
import android.provider.Settings
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationActionReceiverTest {

    private lateinit var receiver: NotificationActionReceiver
    private lateinit var mockContext: Context
    private lateinit var testDispatcher: CoroutineDispatcher
    private lateinit var testScope: TestScope

    private val mockNotificationsRepository: NotificationsRepository = mockk(relaxed = true)
    private var mockDispatcherProvider: DispatcherProvider = mockk(relaxed = true)
    private lateinit var mockNotificationUtils: NotificationUtils.NotificationManager

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)

        MainApplication.applicationScope = testScope

        mockContext = spyk(ApplicationProvider.getApplicationContext<Context>())
        every { mockContext.startActivity(any()) } returns Unit

        every { mockDispatcherProvider.main } returns testDispatcher
        every { mockDispatcherProvider.io } returns testDispatcher
        every { mockDispatcherProvider.default } returns testDispatcher
        every { mockDispatcherProvider.unconfined } returns testDispatcher

        mockNotificationUtils = mockk(relaxed = true)
        mockkStatic(NotificationUtils::class)
        every { NotificationUtils.getInstance(any()) } returns mockNotificationUtils

        mockkStatic("org.ole.planet.myplanet.di.ServiceDependenciesEntryPointKt")
        every { getBroadcastService(any()) } returns mockk(relaxed = true)

        receiver = spyk(NotificationActionReceiver().apply {
            notificationsRepository = mockNotificationsRepository
            dispatcherProvider = mockDispatcherProvider
        })
        try {
            val injectedField = org.ole.planet.myplanet.services.Hilt_NotificationActionReceiver::class.java.getDeclaredField("injected")
            injectedField.isAccessible = true
            injectedField.set(receiver, true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test markNotificationAsRead calls repository and clears notification`() = testScope.runTest {
        val notificationId = "test_notification_id"

        receiver.markNotificationAsRead(mockContext, notificationId)

        advanceUntilIdle()

        coVerify { mockNotificationsRepository.markNotificationsAsRead(setOf(notificationId)) }
    }

    @Test
    fun `test onReceive ACTION_MARK_AS_READ`() = testScope.runTest {
        val notificationId = "test_id"
        val mockIntent = Intent(NotificationUtils.ACTION_MARK_AS_READ)
        mockIntent.putExtra(NotificationUtils.EXTRA_NOTIFICATION_ID, notificationId)

        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        every { receiver.goAsync() } returns pendingResult

        receiver.onReceive(mockContext, mockIntent)
        advanceUntilIdle()

        coVerify { mockNotificationsRepository.markNotificationsAsRead(setOf(notificationId)) }
        verify { mockNotificationUtils.clearNotification(notificationId) }
        verify { pendingResult.finish() }
    }

    @Test
    fun `test onReceive ACTION_STORAGE_SETTINGS`() = testScope.runTest {
        val notificationId = "test_id"
        val mockIntent = Intent(NotificationUtils.ACTION_STORAGE_SETTINGS)
        mockIntent.putExtra(NotificationUtils.EXTRA_NOTIFICATION_ID, notificationId)

        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        every { receiver.goAsync() } returns pendingResult

        receiver.onReceive(mockContext, mockIntent)
        advanceUntilIdle()

        coVerify { mockNotificationsRepository.markNotificationsAsRead(setOf(notificationId)) }
        val intentList = mutableListOf<Intent>()
        verify { mockContext.startActivity(capture(intentList)) }
        assert(intentList.any { it.action == Settings.ACTION_INTERNAL_STORAGE_SETTINGS })
        verify { mockNotificationUtils.clearNotification(notificationId) }
        verify { pendingResult.finish() }
    }

    @Test
    fun `test onReceive ACTION_OPEN_NOTIFICATION`() = testScope.runTest {
        val notificationId = "test_id"
        val notificationType = "type"
        val relatedId = "related_id"

        val mockIntent = Intent(NotificationUtils.ACTION_OPEN_NOTIFICATION)
        mockIntent.putExtra(NotificationUtils.EXTRA_NOTIFICATION_ID, notificationId)
        mockIntent.putExtra(NotificationUtils.EXTRA_NOTIFICATION_TYPE, notificationType)
        mockIntent.putExtra(NotificationUtils.EXTRA_RELATED_ID, relatedId)

        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        every { receiver.goAsync() } returns pendingResult

        receiver.onReceive(mockContext, mockIntent)
        advanceUntilIdle()

        coVerify { mockNotificationsRepository.markNotificationsAsRead(setOf(notificationId)) }
        val intentList = mutableListOf<Intent>()
        verify { mockContext.startActivity(capture(intentList)) }
        val targetIntent = intentList.find { it.getStringExtra("notification_type") == notificationType }
        assert(targetIntent != null)
        assert(targetIntent?.getStringExtra("notification_type") == notificationType)
        assert(targetIntent?.getStringExtra("notification_id") == notificationId)
        assert(targetIntent?.getStringExtra("related_id") == relatedId)

        verify { mockNotificationUtils.clearNotification(notificationId) }
        verify { pendingResult.finish() }
    }
}
