package org.ole.planet.myplanet.services

import android.content.Context
import android.content.Intent
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.verify
import android.content.BroadcastReceiver
import io.mockk.unmockkAll
import io.mockk.unmockkConstructor
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
import dagger.hilt.internal.GeneratedComponentManager

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationActionReceiverTest {

    private lateinit var receiver: NotificationActionReceiver
    private lateinit var mockContext: Context
    private lateinit var mockNotificationsRepository: NotificationsRepository
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

        abstract class MockAppContext : android.app.Application(), dagger.hilt.internal.GeneratedComponentManager<Any>
        val mockApplication = mockk<MockAppContext>(relaxed = true)
        every { mockContext.applicationContext } returns mockApplication
        every { mockApplication.generatedComponent() } returns mockk<NotificationActionReceiver_GeneratedInjector>(relaxed = true)



        mockNotificationsRepository = mockk(relaxed = true)

        mockDispatcherProvider = object : DispatcherProvider {
            override val main: CoroutineDispatcher = testDispatcher
            override val io: CoroutineDispatcher = testDispatcher
            override val default: CoroutineDispatcher = testDispatcher
            override val unconfined: CoroutineDispatcher = testDispatcher
        }

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { call.invocation.self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } answers { call.invocation.self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Boolean>()) } answers { call.invocation.self as Intent }
        every { anyConstructed<Intent>().setFlags(any()) } answers { call.invocation.self as Intent }
        every { anyConstructed<Intent>().setAction(any()) } answers { call.invocation.self as Intent }
        // Property setters in mockk
        // No need to mock property setters if we mock the underlying methods

        mockNotificationUtils = mockk(relaxed = true)
        mockkStatic(NotificationUtils::class)
        every { NotificationUtils.getInstance(any()) } returns mockNotificationUtils

        mockkStatic("org.ole.planet.myplanet.di.ServiceDependenciesEntryPointKt")
        every { getBroadcastService(any()) } returns mockk(relaxed = true)


        receiver = spyk(NotificationActionReceiver().apply {
            notificationsRepository = mockNotificationsRepository
            dispatcherProvider = mockDispatcherProvider
        })
        every { receiver.goAsync() } returns mockk<BroadcastReceiver.PendingResult>(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
        unmockkConstructor(Intent::class)
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
        val mockIntent = mockk<Intent>(relaxed = true)
        every { mockIntent.action } returns NotificationUtils.ACTION_MARK_AS_READ
        every { mockIntent.getStringExtra(NotificationUtils.EXTRA_NOTIFICATION_ID) } returns notificationId

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
        val mockIntent = mockk<Intent>(relaxed = true)
        every { mockIntent.action } returns NotificationUtils.ACTION_STORAGE_SETTINGS
        every { mockIntent.getStringExtra(NotificationUtils.EXTRA_NOTIFICATION_ID) } returns notificationId

        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        every { receiver.goAsync() } returns pendingResult

        receiver.onReceive(mockContext, mockIntent)
        advanceUntilIdle()

        coVerify { mockNotificationsRepository.markNotificationsAsRead(setOf(notificationId)) }
        verify { mockContext.startActivity(any()) }
        verify { mockNotificationUtils.clearNotification(notificationId) }
        verify { pendingResult.finish() }
    }

    @Test
    fun `test onReceive ACTION_OPEN_NOTIFICATION`() = testScope.runTest {
        val notificationId = "test_id"
        val notificationType = "type"
        val relatedId = "related_id"

        val mockIntent = mockk<Intent>(relaxed = true)
        every { mockIntent.action } returns NotificationUtils.ACTION_OPEN_NOTIFICATION
        every { mockIntent.getStringExtra(NotificationUtils.EXTRA_NOTIFICATION_ID) } returns notificationId
        every { mockIntent.getStringExtra(NotificationUtils.EXTRA_NOTIFICATION_TYPE) } returns notificationType
        every { mockIntent.getStringExtra(NotificationUtils.EXTRA_RELATED_ID) } returns relatedId

        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        every { receiver.goAsync() } returns pendingResult

        receiver.onReceive(mockContext, mockIntent)
        advanceUntilIdle()

        coVerify { mockNotificationsRepository.markNotificationsAsRead(setOf(notificationId)) }
        verify { mockContext.startActivity(any()) }
        verify { mockNotificationUtils.clearNotification(notificationId) }
        verify { pendingResult.finish() }
    }
}
