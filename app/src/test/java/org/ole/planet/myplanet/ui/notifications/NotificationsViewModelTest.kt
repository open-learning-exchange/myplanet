package org.ole.planet.myplanet.ui.notifications

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
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
    fun testResolveTypeClassifiesRawTeamTypeAsJoinRequest() = runTest(testDispatcher) {
        val payload = notification(id = "1", type = "team", isRead = false, message = "<b>Jane</b> has requested to join <b>\"My Team\"</b> team.")
        loadNotifications(payload)

        assertEquals("join_request", item("1").notification.type)
    }

    @Test
    fun testResolveTypeClassifiesRawTeamTypeInSpanishAsJoinRequest() = runTest(testDispatcher) {
        val payload = notification(id = "1", type = "team", isRead = false, message = "test22012601 ha solicitado unirse a \"test GT\" team.")
        loadNotifications(payload)

        assertEquals("join_request", item("1").notification.type)
    }

    @Test
    fun testResolveTypeClassifiesRawTeamTypeAsJoinRequestViaSubTypeRegardlessOfMessageLanguage() = runTest(testDispatcher) {
        // Arabic/Nepali/etc. server messages aren't matched by the English/Spanish phrase list, so
        // classification must rely on the structural subType (derived from linkParams) instead.
        val payload = notification(id = "1", type = "team", isRead = false, message = "غير معروف", subType = "join_request")
        loadNotifications(payload)

        assertEquals("join_request", item("1").notification.type)
    }

    @Test
    fun testResolveTypeClassifiesRawTeamTypeAsChatForPostedMessage() = runTest(testDispatcher) {
        val payload = notification(id = "1", type = "team", isRead = false, message = "Bhushan Nim has posted a message on \"test GT\" team.")
        loadNotifications(payload)

        assertEquals("chat", item("1").notification.type)
    }

    @Test
    fun testResolveTypeClassifiesUnmatchedRawTeamTypeAsTeamJoin() = runTest(testDispatcher) {
        val payload = notification(id = "1", type = "team", isRead = false, message = "Has sido eliminado de \"test GT\" team.")
        loadNotifications(payload)

        assertEquals("team_join", item("1").notification.type)
    }

    @Test
    fun testResolveTypeClassifiesRawNewTaskTypeAsTask() = runTest(testDispatcher) {
        val payload = notification(id = "1", type = "newTask", isRead = false, message = "You were assigned a new task")
        loadNotifications(payload)

        assertEquals("task", item("1").notification.type)
    }

    @Test
    fun testResolveTypeClassifiesRawNewResourceTypeAsResource() = runTest(testDispatcher) {
        val payload = notification(id = "1", type = "newResource", isRead = false, message = "Hay nuevos recursos en la biblioteca. ¡Haz clic para verlos!")
        loadNotifications(payload)

        assertEquals("resource", item("1").notification.type)
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
    fun testParseTaskDateRunsOncePerTaskNotification() = runTest(testDispatcher) {
        val task1 = notification(id = "t1", type = "task", isRead = false, message = "Submit report Mon 12, Jan 2024")
        val task2 = notification(id = "t2", type = "task", isRead = false, message = "Review code Fri 7, Feb 2025")

        mockkObject(NotificationsViewModel.Companion)
        try {
            every {
                NotificationsViewModel.parseTaskDate(any())
            } answers { callOriginal() }

            loadNotifications(task1, task2)

            // One parse per task notification — reused for both the team-name title lookup and rendering.
            verify(exactly = 1) { NotificationsViewModel.parseTaskDate(task1.message) }
            verify(exactly = 1) { NotificationsViewModel.parseTaskDate(task2.message) }
        } finally {
            unmockkObject(NotificationsViewModel.Companion)
        }
    }

    private fun item(id: String): NotificationListItem.Item =
        viewModel.groupedItems.value
            .filterIsInstance<NotificationListItem.Item>()
            .first { it.notification.id == id }

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
