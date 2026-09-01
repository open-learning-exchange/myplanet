package org.ole.planet.myplanet.ui.notifications

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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

    @Test
    fun testViewModelDelegatesResolveTypeToRepository() = runTest(testDispatcher) {
        val payload = notification(id = "1", type = "team", isRead = false, message = "whatever", subType = "join_request")
        coEvery { repository.getNotifications(USER_ID, FILTER_ALL, false) } returns listOf(payload)
        every { repository.resolveType("team", "whatever", "join_request") } returns "join_request"
        backgroundScope.launch { viewModel.groupedItems.collect {} }

        viewModel.loadNotifications(USER_ID, FILTER_ALL)
        advanceUntilIdle()

        assertEquals("join_request", item("1").notification.type)
        verify { repository.resolveType("team", "whatever", "join_request") }
    }

    @Test
    fun testLoadNotificationsExtractsRelevantTypesCaseInsensitivelyInOrder() = runTest(testDispatcher) {
        val task1 = notification(id = "t1", type = "TaSk", isRead = false, message = "Task 1", subType = null).copy(relatedId = "rel1")
        val other1 = notification(id = "o1", type = "OTHER", isRead = false, message = "Other 1", subType = null)
        val join1 = notification(id = "j1", type = "joiN_rEquEst", isRead = false, message = "Join 1", subType = null).copy(relatedId = "rel2")
        val task2 = notification(id = "t2", type = "task", isRead = false, message = "Task 2", subType = null).copy(relatedId = "rel3")

        loadNotifications(task1, other1, join1, task2)

        coVerify { repository.getTaskTeamNamesByTaskIds(listOf("rel1", "rel3")) }
        coVerify { repository.getJoinRequestDetailsBatch(listOf("rel2")) }

        val notifs = viewModel.notifications.value
        assertEquals(4, notifs.size)
        assertEquals("t1", notifs[0].id)
        assertEquals("o1", notifs[1].id)
        assertEquals("j1", notifs[2].id)
        assertEquals("t2", notifs[3].id)
    }

    @Test
    fun testLoadNotificationsParallelLookups() = runTest(testDispatcher) {
        val taskWithId = notification(id = "t1", type = "task", isRead = false, message = "Task By Id Mon 12, Jan 2024").copy(relatedId = "rel_task_1")
        val taskWithTitleOnly = notification(id = "t2", type = "task", isRead = false, message = "Task By Title Mon 12, Jan 2024").copy(relatedId = null)
        val joinReq = notification(id = "j1", type = "join_request", isRead = false, message = "Join Req").copy(relatedId = "rel_join_1")

        coEvery { repository.getNotifications(USER_ID, FILTER_ALL, false) } returns listOf(taskWithId, taskWithTitleOnly, joinReq)
        coEvery { repository.getTaskTeamNamesByTaskIds(listOf("rel_task_1")) } returns mapOf("rel_task_1" to "Alpha Team")
        coEvery { repository.getTaskTeamNamesByTaskTitles(listOf("Task By Id", "Task By Title")) } returns mapOf("Task By Title" to "Beta Team")
        coEvery { repository.getJoinRequestDetailsBatch(listOf("rel_join_1")) } returns mapOf("rel_join_1" to Pair("Alice", "Gamma Team"))
        coEvery { repository.getUnreadCount(USER_ID, false) } returns 3

        viewModel.loadNotifications(USER_ID, FILTER_ALL)
        advanceUntilIdle()

        assertEquals(3, viewModel.unreadCount.value)
        val notifications = viewModel.notifications.value
        assertEquals(3, notifications.size)

        coVerify { repository.getTaskTeamNamesByTaskIds(listOf("rel_task_1")) }
        coVerify { repository.getTaskTeamNamesByTaskTitles(listOf("Task By Id", "Task By Title")) }
        coVerify { repository.getJoinRequestDetailsBatch(listOf("rel_join_1")) }
        coVerify { repository.getUnreadCount(USER_ID, false) }
    }

    @Test
    fun testToggleSelectionUpdatesSelectionStateOnListItems() = runTest(testDispatcher) {
        loadNotifications(unreadTask)

        assertFalse(item("1").isSelected)
        assertFalse(item("1").isSelectionMode)

        viewModel.toggleSelection("1")
        runCurrent()

        assertTrue(item("1").isSelected)
        assertTrue(item("1").isSelectionMode)

        viewModel.toggleSelection("1")
        runCurrent()

        assertFalse(item("1").isSelected)
        assertFalse(item("1").isSelectionMode)
    }

    private fun item(id: String): NotificationListItem.Item =
        viewModel.groupedItems.value
            .filterIsInstance<NotificationListItem.Item>()
            .first { it.notification.id == id }

    private fun TestScope.loadNotifications(vararg payloads: NotificationPayload) {
        coEvery { repository.getNotifications(USER_ID, FILTER_ALL, false) } returns payloads.toList()
        // The classifier now lives in the repository; for ViewModel behavior tests stub it to echo the
        // (lowercased) raw type so grouping/expansion still exercise the ViewModel wiring. Real
        // classification is covered in NotificationsRepositoryImplTest.
        every { repository.resolveType(any(), any(), any()) } answers {
            firstArg<String>().lowercase()
        }
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

        private fun notification(id: String, type: String, isRead: Boolean, message: String = "message $id", subType: String? = null) = NotificationPayload(
            id = id,
            userId = USER_ID,
            message = message,
            isRead = isRead,
            createdAt = 0L,
            type = type,
            relatedId = null,
            title = null,
            link = null,
            priority = 0,
            isFromServer = false,
            rev = null,
            needsSync = false,
            subType = subType
        )
    }
}
