package org.ole.planet.myplanet.services

import android.content.Context
import android.content.Intent
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
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
        mockNotificationsRepository = mockk(relaxed = true)

        mockDispatcherProvider = object : DispatcherProvider {
            override val main: CoroutineDispatcher = testDispatcher
            override val io: CoroutineDispatcher = testDispatcher
            override val default: CoroutineDispatcher = testDispatcher
            override val unconfined: CoroutineDispatcher = testDispatcher
        }

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { mockk() }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } answers { mockk() }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Boolean>()) } answers { mockk() }
        every { anyConstructed<Intent>().flags = any() } returns Unit
        every { anyConstructed<Intent>().action = any() } returns Unit

        mockNotificationUtils = mockk(relaxed = true)
        mockkStatic(NotificationUtils::class)
        every { NotificationUtils.getInstance(any()) } returns mockNotificationUtils

        mockkStatic("org.ole.planet.myplanet.di.ServiceDependenciesEntryPointKt")
        every { getBroadcastService(any()) } returns mockk(relaxed = true)


        receiver = NotificationActionReceiver().apply {
            notificationsRepository = mockNotificationsRepository
            dispatcherProvider = mockDispatcherProvider
        }
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
}
