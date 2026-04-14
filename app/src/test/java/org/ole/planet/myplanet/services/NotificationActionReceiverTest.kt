package org.ole.planet.myplanet.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
    private lateinit var mockNotificationsRepository: NotificationsRepository
    private lateinit var mockPendingResult: BroadcastReceiver.PendingResult
    private lateinit var mockNotificationUtils: NotificationUtils.NotificationManager
    private lateinit var testDispatcher: CoroutineDispatcher
    private lateinit var testScope: TestScope

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        MainApplication.applicationScope = testScope

        mockNotificationsRepository = mockk(relaxed = true)
        mockNotificationUtils = mockk(relaxed = true)

        mockkStatic(NotificationUtils::class)
        every { NotificationUtils.getInstance(any()) } returns mockNotificationUtils

        mockkStatic("org.ole.planet.myplanet.di.ServiceDependenciesEntryPointKt")
        every { getBroadcastService(any()) } returns mockk(relaxed = true)

        mockPendingResult = mockk(relaxed = true)

        receiver = NotificationActionReceiver().apply {
            notificationsRepository = mockNotificationsRepository
            dispatcherProvider = object : DispatcherProvider {
                override val main: CoroutineDispatcher = testDispatcher
                override val io: CoroutineDispatcher = testDispatcher
                override val default: CoroutineDispatcher = testDispatcher
                override val unconfined: CoroutineDispatcher = testDispatcher
            }
            pendingResultProvider = { mockPendingResult }
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test markNotificationAsRead calls repository`() = testScope.runTest {
        val mockContext = mockk<Context>(relaxed = true)
        val notificationId = "test_notification_id"
        receiver.markNotificationAsRead(mockContext, notificationId)
        advanceUntilIdle()
        coVerify { mockNotificationsRepository.markNotificationsAsRead(setOf(notificationId)) }
    }

    @Test
    fun `onReceive with ACTION_MARK_AS_READ should mark as read and clear notification`() = testScope.runTest {
        val mockContext = mockk<Context>(relaxed = true)
        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns NotificationUtils.ACTION_MARK_AS_READ
        every { intent.getStringExtra(NotificationUtils.EXTRA_NOTIFICATION_ID) } returns "test_id"

        receiver.onReceive(mockContext, intent)
        advanceUntilIdle()

        coVerify { mockNotificationsRepository.markNotificationsAsRead(setOf("test_id")) }
        verify { mockNotificationUtils.clearNotification("test_id") }
        verify { mockPendingResult.finish() }
    }

    @Test
    fun `onReceive with ACTION_STORAGE_SETTINGS should mark as read, start activity and clear notification`() = testScope.runTest {
        val mockContext = mockk<Context>(relaxed = true)
        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns NotificationUtils.ACTION_STORAGE_SETTINGS
        every { intent.getStringExtra(NotificationUtils.EXTRA_NOTIFICATION_ID) } returns "test_id"

        receiver.onReceive(mockContext, intent)
        advanceUntilIdle()

        coVerify { mockNotificationsRepository.markNotificationsAsRead(setOf("test_id")) }
        verify { mockContext.startActivity(any()) }
        verify { mockNotificationUtils.clearNotification("test_id") }
        verify { mockPendingResult.finish() }
    }

    @Test
    fun `onReceive with ACTION_OPEN_NOTIFICATION should mark as read, start activity and clear notification`() = testScope.runTest {
        val mockContext = mockk<Context>(relaxed = true)
        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns NotificationUtils.ACTION_OPEN_NOTIFICATION
        every { intent.getStringExtra(NotificationUtils.EXTRA_NOTIFICATION_ID) } returns "test_id"
        every { intent.getStringExtra(NotificationUtils.EXTRA_NOTIFICATION_TYPE) } returns "type"
        every { intent.getStringExtra(NotificationUtils.EXTRA_RELATED_ID) } returns "related"

        receiver.onReceive(mockContext, intent)
        advanceUntilIdle()

        coVerify { mockNotificationsRepository.markNotificationsAsRead(setOf("test_id")) }
        verify { mockContext.startActivity(any()) }
        verify { mockNotificationUtils.clearNotification("test_id") }
        verify { mockPendingResult.finish() }
    }

    @Test
    fun `onReceive with null action should just finish pending result`() = testScope.runTest {
        val mockContext = mockk<Context>(relaxed = true)
        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns null

        receiver.onReceive(mockContext, intent)
        advanceUntilIdle()

        verify { mockPendingResult.finish() }
        coVerify(exactly = 0) { mockNotificationsRepository.markNotificationsAsRead(any()) }
    }

    @Test
    fun `onReceive with unknown action should just finish pending result`() = testScope.runTest {
        val mockContext = mockk<Context>(relaxed = true)
        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns "UNKNOWN"

        receiver.onReceive(mockContext, intent)
        advanceUntilIdle()

        verify { mockPendingResult.finish() }
        coVerify(exactly = 0) { mockNotificationsRepository.markNotificationsAsRead(any()) }
    }

    @Test
    fun `onReceive with null notificationId should work but not clear notification`() = testScope.runTest {
        val mockContext = mockk<Context>(relaxed = true)
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
