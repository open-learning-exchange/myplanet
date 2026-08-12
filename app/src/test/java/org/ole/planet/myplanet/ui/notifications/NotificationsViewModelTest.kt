package org.ole.planet.myplanet.ui.notifications

import android.content.Context
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var repository: NotificationsRepository
    private lateinit var viewModel: NotificationsViewModel

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        viewModel = NotificationsViewModel(repository, mockk<Context>(relaxed = true))
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
    fun testGroupIsExpandedByDefaultOnlyWhenItHasUnread() = runTest(testDispatcher) {
        loadNotifications(unreadTask, readResource)

        assertTrue(header("task").isExpanded)
        assertFalse(header("resource").isExpanded)
    }

    @Test
    fun testToggleGroupExpansionOverridesTheDefault() = runTest(testDispatcher) {
        loadNotifications(unreadTask, readResource)

        viewModel.toggleGroupExpansion("task")
        viewModel.toggleGroupExpansion("resource")
        runCurrent()

        assertFalse(header("task").isExpanded)
        assertTrue(header("resource").isExpanded)
    }

    @Test
    fun testToggleGroupExpansionTwiceRestoresTheDefault() = runTest(testDispatcher) {
        loadNotifications(unreadTask, readResource)

        repeat(2) { viewModel.toggleGroupExpansion("task") }
        runCurrent()

        assertTrue(header("task").isExpanded)
    }

    @Test
    fun testMarkAllAsReadClearsOverridesAndCollapsesEveryGroup() = runTest(testDispatcher) {
        loadNotifications(unreadTask, readResource)
        coEvery { repository.markAllUnreadAsRead(USER_ID) } returns setOf(unreadTask.id)

        viewModel.toggleGroupExpansion("resource")
        runCurrent()
        assertTrue(header("resource").isExpanded)

        viewModel.markAllAsRead(USER_ID)
        advanceUntilIdle()

        assertEquals(0, viewModel.unreadCount.value)
        assertFalse(header("task").isExpanded)
        assertFalse(header("resource").isExpanded)
    }

    private fun TestScope.loadNotifications(vararg payloads: NotificationPayload) {
        coEvery { repository.getNotifications(USER_ID, FILTER_ALL, false) } returns payloads.toList()
        backgroundScope.launch { viewModel.groupedItems.collect {} }
        viewModel.loadNotifications(USER_ID, FILTER_ALL)
        advanceUntilIdle()
    }

    private fun header(type: String): NotificationListItem.Header =
        viewModel.groupedItems.value
            .filterIsInstance<NotificationListItem.Header>()
            .first { it.type == type }

    companion object {
        private const val USER_ID = "user1"
        private const val FILTER_ALL = "all"

        private val unreadTask = notification(id = "1", type = "task", isRead = false)
        private val readResource = notification(id = "2", type = "resource", isRead = true)

        private fun notification(id: String, type: String, isRead: Boolean) = NotificationPayload(
            id = id,
            userId = USER_ID,
            message = "message $id",
            isRead = isRead,
            createdAt = 0L,
            type = type,
            relatedId = null,
            title = null,
            link = null,
            priority = 0,
            isFromServer = false,
            rev = null,
            needsSync = false
        )
    }
}
