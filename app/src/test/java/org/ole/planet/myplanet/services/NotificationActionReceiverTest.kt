package org.ole.planet.myplanet.services

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.di.getBroadcastService
import org.ole.planet.myplanet.repository.NotificationsRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.NotificationUtils
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q], application = Application::class)
class NotificationActionReceiverTest {

    private lateinit var receiver: NotificationActionReceiver
    private lateinit var mockContext: Context
    private lateinit var mockNotificationsRepository: NotificationsRepository
    private lateinit var mockPendingResult: BroadcastReceiver.PendingResult
    private lateinit var mockNotificationUtils: NotificationUtils.NotificationManager
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        MainApplication.applicationScope = CoroutineScope(testDispatcher)

        mockContext = mockk<Context>(relaxed = true)
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
    fun `test markNotificationAsRead calls repository`() = runTest(testDispatcher) {
        val notificationId = "test_notification_id"
        receiver.markNotificationAsRead(mockContext, notificationId)
        coVerify { mockNotificationsRepository.markNotificationsAsRead(setOf(notificationId)) }
    }

    @Test
    fun `onReceive with ACTION_MARK_AS_READ should mark as read and clear notification`() = runTest(testDispatcher) {
        val intent = Intent(NotificationUtils.ACTION_MARK_AS_READ).apply {
            putExtra(NotificationUtils.EXTRA_NOTIFICATION_ID, "test_id")
        }

        receiver.onReceive(mockContext, intent)

        coVerify { mockNotificationsRepository.markNotificationsAsRead(setOf("test_id")) }
        verify { mockNotificationUtils.clearNotification("test_id") }
        verify { mockPendingResult.finish() }
    }

    @Test
    fun `onReceive with ACTION_STORAGE_SETTINGS should mark as read and start activity`() = runTest(testDispatcher) {
        val intent = Intent(NotificationUtils.ACTION_STORAGE_SETTINGS).apply {
            putExtra(NotificationUtils.EXTRA_NOTIFICATION_ID, "test_id")
        }

        receiver.onReceive(mockContext, intent)

        coVerify { mockNotificationsRepository.markNotificationsAsRead(setOf("test_id")) }
        verify { mockContext.startActivity(any()) }
        verify { mockNotificationUtils.clearNotification("test_id") }
        verify { mockPendingResult.finish() }
    }

    @Test
    fun `onReceive with ACTION_OPEN_NOTIFICATION should mark as read and start dashboard`() = runTest(testDispatcher) {
        val intent = Intent(NotificationUtils.ACTION_OPEN_NOTIFICATION).apply {
            putExtra(NotificationUtils.EXTRA_NOTIFICATION_ID, "test_id")
            putExtra(NotificationUtils.EXTRA_NOTIFICATION_TYPE, "type")
            putExtra(NotificationUtils.EXTRA_RELATED_ID, "related")
        }

        receiver.onReceive(mockContext, intent)

        coVerify { mockNotificationsRepository.markNotificationsAsRead(setOf("test_id")) }
        verify { mockContext.startActivity(any()) }
        verify { mockNotificationUtils.clearNotification("test_id") }
        verify { mockPendingResult.finish() }
    }

    @Test
    fun `onReceive with null action should just finish`() = runTest(testDispatcher) {
        val intent = Intent()

        receiver.onReceive(mockContext, intent)

        verify { mockPendingResult.finish() }
        coVerify(exactly = 0) { mockNotificationsRepository.markNotificationsAsRead(any()) }
    }

    @Test
    fun `onReceive with unknown action should just finish`() = runTest(testDispatcher) {
        val intent = Intent("UNKNOWN")

        receiver.onReceive(mockContext, intent)

        verify { mockPendingResult.finish() }
        coVerify(exactly = 0) { mockNotificationsRepository.markNotificationsAsRead(any()) }
    }

    @Test
    fun `onReceive with null notificationId should work but not clear`() = runTest(testDispatcher) {
        val intent = Intent(NotificationUtils.ACTION_MARK_AS_READ)

        receiver.onReceive(mockContext, intent)

        verify(exactly = 0) { mockNotificationUtils.clearNotification(any()) }
        verify { mockPendingResult.finish() }
    }
}
