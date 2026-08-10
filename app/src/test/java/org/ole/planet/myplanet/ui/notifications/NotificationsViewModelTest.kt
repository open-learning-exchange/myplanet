package org.ole.planet.myplanet.ui.notifications

import android.content.Context
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.NotificationListItem
import org.ole.planet.myplanet.model.NotificationPayload
import org.ole.planet.myplanet.repository.NotificationsRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context = mockk<Context>(relaxed = true)
    private val notificationsRepository = mockk<NotificationsRepository>(relaxed = true)
    private lateinit var viewModel: NotificationsViewModel

    @Before
    fun setup() {
        io.mockk.every { context.getString(any()) } returns "MockedString"
        viewModel = NotificationsViewModel(notificationsRepository, context)
    }

    @Test
    fun testParseTaskDate_withValidDate() {
        val message = "Complete math assignment Mon 12, Jan 2024"
        val result = NotificationsViewModel.parseTaskDate(message)
        assertEquals("Complete math assignment", result?.first)
        assertEquals("Mon 12, Jan 2024", result?.second)
    }

    @Test
    fun testParseTaskDate_withNoDate() {
        val message = "Complete math assignment as soon as possible"
        val result = NotificationsViewModel.parseTaskDate(message)
        assertNull(result)
    }

    @Test
    fun testFormatStorageNotification_runningLow10() {
        val result = NotificationsViewModel.formatStorageNotification(
            message = "10%",
            storageRunningLowStr = "Storage running low:",
            storageAvailableStr = "Storage available:"
        )
        assertEquals("Storage running low: 10%", result)
    }

    @Test
    fun testFormatStorageNotification_runningLow40() {
        val result = NotificationsViewModel.formatStorageNotification(
            message = "40%",
            storageRunningLowStr = "Storage running low:",
            storageAvailableStr = "Storage available:"
        )
        assertEquals("Storage running low: 40%", result)
    }

    @Test
    fun testFormatStorageNotification_available() {
        val result = NotificationsViewModel.formatStorageNotification(
            message = "50%",
            storageRunningLowStr = "Storage running low:",
            storageAvailableStr = "Storage available:"
        )
        assertEquals("Storage available: 50%", result)
    }

    @Test
    fun testFormatStorageNotification_invalidInt() {
        val result = NotificationsViewModel.formatStorageNotification(
            message = "not_an_int",
            storageRunningLowStr = "Storage running low:",
            storageAvailableStr = "Storage available:"
        )
        assertEquals("not_an_int", result)
    }

    @Test
    fun testFormatJoinRequestNotification() {
        val result = NotificationsViewModel.formatJoinRequestNotification(
            prefixStr = "Join Request",
            userRequestedToJoinTeamStr = "John Doe requested to join Awesome Team"
        )
        assertEquals("<b>Join Request</b> John Doe requested to join Awesome Team", result)
    }

    @Test
    fun testDefaultGroupExpansion() = runTest {
        val notificationsList = listOf(
            NotificationPayload(
                id = "notif_unread_join",
                userId = "user_1",
                message = "Join Request from John",
                isRead = false,
                createdAt = 123456789L,
                type = "join_request",
                relatedId = "rel_1",
                title = null,
                link = null,
                priority = 1,
                isFromServer = false,
                rev = null,
                needsSync = false
            ),
            NotificationPayload(
                id = "notif_read_chat",
                userId = "user_1",
                message = "New chat message",
                isRead = true,
                createdAt = 123456789L,
                type = "chat",
                relatedId = null,
                title = null,
                link = null,
                priority = 1,
                isFromServer = false,
                rev = null,
                needsSync = false
            )
        )

        coEvery { notificationsRepository.getNotifications("user_1", "all", false) } returns notificationsList
        coEvery { notificationsRepository.getUnreadCount("user_1", false) } returns 1

        val job = launch {
            viewModel.groupedItems.collect {}
        }

        viewModel.loadNotifications("user_1", "all", false)
        testScheduler.advanceUntilIdle()

        val items = viewModel.groupedItems.value
        assertTrue(items.isNotEmpty())

        // "join_request" should be default expanded because it has unread count > 0
        val joinHeader = items.filterIsInstance<NotificationListItem.Header>()
            .find { it.type == "join_request" }
        assertNotNull(joinHeader)
        assertTrue(joinHeader!!.isExpanded)
        assertEquals(1, joinHeader.unreadCount)

        // "chat" should be default collapsed because it has 0 unread notifications
        val chatHeader = items.filterIsInstance<NotificationListItem.Header>()
            .find { it.type == "chat" }
        assertNotNull(chatHeader)
        assertFalse(chatHeader!!.isExpanded)
        assertEquals(0, chatHeader.unreadCount)

        job.cancel()
    }

    @Test
    fun testExplicitToggleOverrides() = runTest {
        val notificationsList = listOf(
            NotificationPayload(
                id = "notif_unread_join",
                userId = "user_1",
                message = "Join Request from John",
                isRead = false,
                createdAt = 123456789L,
                type = "join_request",
                relatedId = "rel_1",
                title = null,
                link = null,
                priority = 1,
                isFromServer = false,
                rev = null,
                needsSync = false
            ),
            NotificationPayload(
                id = "notif_read_chat",
                userId = "user_1",
                message = "New chat message",
                isRead = true,
                createdAt = 123456789L,
                type = "chat",
                relatedId = null,
                title = null,
                link = null,
                priority = 1,
                isFromServer = false,
                rev = null,
                needsSync = false
            )
        )

        coEvery { notificationsRepository.getNotifications("user_1", "all", false) } returns notificationsList

        val job = launch {
            viewModel.groupedItems.collect {}
        }

        viewModel.loadNotifications("user_1", "all", false)
        testScheduler.advanceUntilIdle()

        // 1. Explicitly collapse the unread (default expanded) group
        viewModel.toggleGroupExpansion("join_request")
        testScheduler.advanceUntilIdle()

        val itemsCollapsed = viewModel.groupedItems.value
        val joinHeaderCollapsed = itemsCollapsed.filterIsInstance<NotificationListItem.Header>()
            .find { it.type == "join_request" }
        assertNotNull(joinHeaderCollapsed)
        assertFalse(joinHeaderCollapsed!!.isExpanded)

        // 2. Explicitly expand the read (default collapsed) group
        viewModel.toggleGroupExpansion("chat")
        testScheduler.advanceUntilIdle()

        val itemsExpanded = viewModel.groupedItems.value
        val chatHeaderExpanded = itemsExpanded.filterIsInstance<NotificationListItem.Header>()
            .find { it.type == "chat" }
        assertNotNull(chatHeaderExpanded)
        assertTrue(chatHeaderExpanded!!.isExpanded)

        // 3. Toggle join_request back to expanded and verify mutual exclusivity of sets
        viewModel.toggleGroupExpansion("join_request")
        testScheduler.advanceUntilIdle()

        val finalItems = viewModel.groupedItems.value
        val joinHeaderFinal = finalItems.filterIsInstance<NotificationListItem.Header>()
            .find { it.type == "join_request" }
        assertNotNull(joinHeaderFinal)
        assertTrue(joinHeaderFinal!!.isExpanded)

        job.cancel()
    }

    @Test
    fun testMarkAllAsReadResetsExplicitSets() = runTest {
        val notificationsList = listOf(
            NotificationPayload(
                id = "notif_unread_join",
                userId = "user_1",
                message = "Join Request from John",
                isRead = false,
                createdAt = 123456789L,
                type = "join_request",
                relatedId = "rel_1",
                title = null,
                link = null,
                priority = 1,
                isFromServer = false,
                rev = null,
                needsSync = false
            )
        )

        coEvery { notificationsRepository.getNotifications("user_1", "all", false) } returns notificationsList
        // When we mark all as read, we return marked notification IDs
        coEvery { notificationsRepository.markAllUnreadAsRead("user_1") } returns setOf("notif_unread_join")

        val job = launch {
            viewModel.groupedItems.collect {}
        }

        viewModel.loadNotifications("user_1", "all", false)
        testScheduler.advanceUntilIdle()

        // Explicitly collapse it first to have it in _collapsedGroups
        viewModel.toggleGroupExpansion("join_request")
        testScheduler.advanceUntilIdle()

        // Toggle again to explicitly expand it to have it in _expandedGroups
        viewModel.toggleGroupExpansion("join_request")
        testScheduler.advanceUntilIdle()

        // Call markAllAsRead
        viewModel.markAllAsRead("user_1")
        testScheduler.advanceUntilIdle()

        // The group should now reset to default (collapsed, since all notifications are now read)
        val finalItems = viewModel.groupedItems.value
        val joinHeaderFinal = finalItems.filterIsInstance<NotificationListItem.Header>()
            .find { it.type == "join_request" }
        assertNotNull(joinHeaderFinal)
        assertFalse(joinHeaderFinal!!.isExpanded) // Default collapsed because it's now read

        job.cancel()
    }
}
