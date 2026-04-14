package org.ole.planet.myplanet.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
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

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationActionReceiverTest {

    private lateinit var receiver: NotificationActionReceiver
    private lateinit var mockContext: Context
    private lateinit var mockNotificationsRepository: NotificationsRepository
    private lateinit var mockPendingResult: BroadcastReceiver.PendingResult
    private lateinit var testDispatcher: CoroutineDispatcher
    private lateinit var mockDispatcherProvider: DispatcherProvider
    private lateinit var testScope: TestScope
    private lateinit var mockNotificationUtils: NotificationUtils.NotificationManager

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        MainApplication.applicationScope = testScope

        mockContext = mockk<Context>(relaxed = true)
        mockNotificationsRepository = mockk(relaxed = true)
        mockNotificationUtils = mockk(relaxed = true)

        mockDispatcherProvider = object : DispatcherProvider {
            override val main: CoroutineDispatcher = testDispatcher
            override val io: CoroutineDispatcher = testDispatcher
            override val default: CoroutineDispatcher = testDispatcher
            override val unconfined: CoroutineDispatcher = testDispatcher
        }

        mockkStatic(NotificationUtils::class)
        every { NotificationUtils.getInstance(any()) } returns mockNotificationUtils

        mockkStatic("org.ole.planet.myplanet.di.ServiceDependenciesEntryPointKt")
        every { getBroadcastService(any()) } returns mockk(relaxed = true)

        mockkConstructor(Intent::class, relaxed = true)

        mockPendingResult = mockk(relaxed = true)
        receiver = NotificationActionReceiver().apply {
            notificationsRepository = mockNotificationsRepository
            dispatcherProvider = mockDispatcherProvider
            pendingResultProvider = { mockPendingResult }
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `onReceive with ACTION_MARK_AS_READ should mark as read and clear notification`() = testScope.runTest {
        val intent = mockk<Intent>(relaxed = true)
        val notificationId = "test_id"
        every { intent.action } returns NotificationUtils.ACTION_MARK_AS_READ
        every { intent.getStringExtra(NotificationUtils.EXTRA_NOTIFICATION_ID) } returns notificationId

        receiver.onReceive(mockContext, intent)
        advanceUntilIdle()

        coVerify { mockNotificationsRepository.markNotificationsAsRead(setOf(notificationId)) }
        verify { mockNotificationUtils.clearNotification(notificationId) }
        verify { mockPendingResult.finish() }
    }

    @Test
    fun `onReceive with ACTION_STORAGE_SETTINGS should mark as read, start activity and clear notification`() = testScope.runTest {
        val intent = mockk<Intent>(relaxed = true)
        val notificationId = "test_id"
        every { intent.action } returns NotificationUtils.ACTION_STORAGE_SETTINGS
        every { intent.getStringExtra(NotificationUtils.EXTRA_NOTIFICATION_ID) } returns notificationId

        receiver.onReceive(mockContext, intent)
        advanceUntilIdle()

        coVerify { mockNotificationsRepository.markNotificationsAsRead(setOf(notificationId)) }
        verify { mockContext.startActivity(any()) }
        verify { mockNotificationUtils.clearNotification(notificationId) }
        verify { mockPendingResult.finish() }
    }

    @Test
    fun `onReceive with ACTION_OPEN_NOTIFICATION should mark as read, start activity and clear notification`() = testScope.runTest {
        val intent = mockk<Intent>(relaxed = true)
        val notificationId = "test_id"
        every { intent.action } returns NotificationUtils.ACTION_OPEN_NOTIFICATION
        every { intent.getStringExtra(NotificationUtils.EXTRA_NOTIFICATION_ID) } returns notificationId
        every { intent.getStringExtra(NotificationUtils.EXTRA_NOTIFICATION_TYPE) } returns "test_type"
        every { intent.getStringExtra(NotificationUtils.EXTRA_RELATED_ID) } returns "related_id"

        receiver.onReceive(mockContext, intent)
        advanceUntilIdle()

        coVerify { mockNotificationsRepository.markNotificationsAsRead(setOf(notificationId)) }
        verify { mockContext.startActivity(any()) }
        verify { mockNotificationUtils.clearNotification(notificationId) }
        verify { mockPendingResult.finish() }
    }

    @Test
    fun `onReceive with null action should just finish pending result`() = testScope.runTest {
        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns null

        receiver.onReceive(mockContext, intent)
        advanceUntilIdle()

        verify { mockPendingResult.finish() }
        coVerify(exactly = 0) { mockNotificationsRepository.markNotificationsAsRead(any()) }
    }

    @Test
    fun `onReceive with unknown action should just finish pending result`() = testScope.runTest {
        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns "UNKNOWN_ACTION"

        receiver.onReceive(mockContext, intent)
        advanceUntilIdle()

        verify { mockPendingResult.finish() }
        coVerify(exactly = 0) { mockNotificationsRepository.markNotificationsAsRead(any()) }
    }

    @Test
    fun `onReceive with null notificationId should still work but not clear notification`() = testScope.runTest {
        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns NotificationUtils.ACTION_MARK_AS_READ
        every { intent.getStringExtra(NotificationUtils.EXTRA_NOTIFICATION_ID) } returns null

        receiver.onReceive(mockContext, intent)
        advanceUntilIdle()

        coVerify(exactly = 0) { mockNotificationsRepository.markNotificationsAsRead(any()) }
        verify(exactly = 0) { mockNotificationUtils.clearNotification(any()) }
        verify { mockPendingResult.finish() }
    }
}
