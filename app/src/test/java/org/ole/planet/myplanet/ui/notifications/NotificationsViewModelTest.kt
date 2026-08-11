package org.ole.planet.myplanet.ui.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Rule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {

    private val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = org.ole.planet.myplanet.utils.MainDispatcherRule(testDispatcher)

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
    fun testGroupExpansionTriStateBehavior() = kotlinx.coroutines.test.runTest(testDispatcher) {
        val context = io.mockk.mockk<android.content.Context>(relaxed = true)
        io.mockk.every { context.getString(org.ole.planet.myplanet.R.string.tasks) } returns "Tasks"
        io.mockk.every { context.getString(org.ole.planet.myplanet.R.string.resources) } returns "Resources"

        val repository = io.mockk.mockk<org.ole.planet.myplanet.repository.NotificationsRepository>(relaxed = true)

        // Explicitly stub all repository methods called during loadNotifications to avoid implicit mock NPEs
        io.mockk.coEvery { repository.getTaskTeamNamesByTaskIds(any()) } returns emptyMap()
        io.mockk.coEvery { repository.getTaskTeamNamesByTaskTitles(any()) } returns emptyMap()
        io.mockk.coEvery { repository.getJoinRequestDetailsBatch(any()) } returns emptyMap()
        io.mockk.coEvery { repository.getJoinRequestDetails(any()) } returns Pair("Unknown", "Unknown")
        io.mockk.coEvery { repository.getUnreadCount(any(), any()) } returns 0

        val viewModel = NotificationsViewModel(repository, context)

        // Start collecting groupedItems to trigger subscription
        val collectJob = backgroundScope.launch {
            viewModel.groupedItems.collect {}
        }

        val payload1 = org.ole.planet.myplanet.model.NotificationPayload(
            id = "1",
            userId = "user1",
            message = "Task 1",
            isRead = false,
            createdAt = 100L,
            type = "task",
            relatedId = null,
            title = null,
            link = null,
            priority = 0,
            isFromServer = false,
            rev = null,
            needsSync = false
        )
        val payload2 = org.ole.planet.myplanet.model.NotificationPayload(
            id = "2",
            userId = "user1",
            message = "Resource 1",
            isRead = true,
            createdAt = 100L,
            type = "resource",
            relatedId = null,
            title = null,
            link = null,
            priority = 0,
            isFromServer = false,
            rev = null,
            needsSync = false
        )
        io.mockk.coEvery { repository.getNotifications("user1", "all", false) } returns listOf(payload1, payload2)

        viewModel.loadNotifications("user1", "all", false)

        // Wait for coroutines to execute
        testDispatcher.scheduler.advanceUntilIdle()

        var groups = viewModel.groupedItems.value
        println("DEBUG: groups size is ${groups.size}")
        groups.forEach { println("DEBUG: item is $it") }

        // Let's print notifications too
        println("DEBUG: notifications size is ${viewModel.notifications.value.size}")

        // 1. Check default derived states from unread counts:
        // "task" group has unread task -> should be expanded.
        // "resource" group has only read resource -> should be collapsed.
        val taskHeaderBeforeToggle = groups.find { it is org.ole.planet.myplanet.model.NotificationListItem.Header && it.type == "task" } as org.ole.planet.myplanet.model.NotificationListItem.Header
        val resourceHeaderBeforeToggle = groups.find { it is org.ole.planet.myplanet.model.NotificationListItem.Header && it.type == "resource" } as org.ole.planet.myplanet.model.NotificationListItem.Header

        assertTrue(taskHeaderBeforeToggle.isExpanded)
        assertTrue(!resourceHeaderBeforeToggle.isExpanded)

        // 2. Explicit Toggle Overrides:
        // Toggle "task" -> should explicitly collapse it.
        viewModel.toggleGroupExpansion("task")
        runCurrent()
        groups = viewModel.groupedItems.value
        val taskHeaderAfterToggle = groups.find { it is org.ole.planet.myplanet.model.NotificationListItem.Header && it.type == "task" } as org.ole.planet.myplanet.model.NotificationListItem.Header
        assertTrue(!taskHeaderAfterToggle.isExpanded)

        // Toggle "resource" -> should explicitly expand it.
        viewModel.toggleGroupExpansion("resource")
        runCurrent()
        groups = viewModel.groupedItems.value
        val resourceHeaderAfterToggle = groups.find { it is org.ole.planet.myplanet.model.NotificationListItem.Header && it.type == "resource" } as org.ole.planet.myplanet.model.NotificationListItem.Header
        assertTrue(resourceHeaderAfterToggle.isExpanded)

        // Toggle "task" again -> should explicitly expand it.
        viewModel.toggleGroupExpansion("task")
        runCurrent()
        groups = viewModel.groupedItems.value
        val taskHeaderAfterToggle2 = groups.find { it is org.ole.planet.myplanet.model.NotificationListItem.Header && it.type == "task" } as org.ole.planet.myplanet.model.NotificationListItem.Header
        assertTrue(taskHeaderAfterToggle2.isExpanded)

        // 3. Mark all as read resets the explicit sets:
        io.mockk.coEvery { repository.markAllUnreadAsRead("user1") } returns setOf("1")
        viewModel.markAllAsRead("user1")
        testDispatcher.scheduler.advanceUntilIdle()
        groups = viewModel.groupedItems.value

        // After markAllAsRead, the groups should collapse since there are no unreads, and explicit overrides are cleared.
        val taskHeaderAfterMarkAll = groups.find { it is org.ole.planet.myplanet.model.NotificationListItem.Header && it.type == "task" } as org.ole.planet.myplanet.model.NotificationListItem.Header
        val resourceHeaderAfterMarkAll = groups.find { it is org.ole.planet.myplanet.model.NotificationListItem.Header && it.type == "resource" } as org.ole.planet.myplanet.model.NotificationListItem.Header

        assertTrue(!taskHeaderAfterMarkAll.isExpanded)
        assertTrue(!resourceHeaderAfterMarkAll.isExpanded)
    }
}
