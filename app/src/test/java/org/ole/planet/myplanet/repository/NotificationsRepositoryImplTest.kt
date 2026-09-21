package org.ole.planet.myplanet.repository

import android.app.Application
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.dao.NotificationDao
import org.ole.planet.myplanet.data.room.dao.TeamNotificationDao
import org.ole.planet.myplanet.data.room.dao.TeamTaskDao
import org.ole.planet.myplanet.model.AppNotification
import org.ole.planet.myplanet.model.TeamNotification
import org.ole.planet.myplanet.utils.TestTimeProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@ExperimentalCoroutinesApi
class NotificationsRepositoryImplTest {

    private lateinit var userRepository: dagger.Lazy<UserRepository>
    private lateinit var teamsRepository: dagger.Lazy<TeamsNotificationsRepository>
    private lateinit var repository: NotificationsRepositoryImpl
    private lateinit var teamNotificationDao: TeamNotificationDao
    private lateinit var notificationDao: NotificationDao
    private lateinit var teamTaskDao: TeamTaskDao
    private lateinit var voicesRepository: VoicesRepository

    @Before
    fun setUp() {
        userRepository = mockk(relaxed = true)
        teamsRepository = mockk(relaxed = true)
        teamNotificationDao = mockk(relaxed = true)
        notificationDao = mockk(relaxed = true)
        teamTaskDao = mockk(relaxed = true)
        voicesRepository = mockk(relaxed = true)
        repository = NotificationsRepositoryImpl(
            userRepository,
            teamsRepository,
            TestTimeProvider(),
            teamNotificationDao,
            notificationDao,
            teamTaskDao,
            voicesRepository
        )
    }

    @Test
    fun `test default property values`() {
        val notification = AppNotification()
        assertNotNull(notification.id)
        assertEquals("", notification.userId)
        assertEquals("", notification.message)
        assertFalse(notification.isRead)
        assertNotNull(notification.createdAt)
        assertEquals("", notification.type)
        assertEquals(null, notification.relatedId)
        assertEquals(null, notification.title)
        assertEquals(null, notification.link)
        assertEquals(0, notification.priority)
        assertFalse(notification.isFromServer)
        assertEquals(null, notification.rev)
        assertFalse(notification.needsSync)
    }

    @Test
    fun `insert with missing id does nothing`() = runTest {
        val jsonObject = JsonObject()

        repository.insert(jsonObject)

        coVerify(exactly = 0) { notificationDao.upsert(any()) }
    }

    @Test
    fun `insert creates new notification when not found`() = runTest {
        val jsonObject = JsonObject().apply {
            addProperty("_id", "testId")
            addProperty("user", "testUser")
            addProperty("message", "testMessage")
            addProperty("type", "testType")
            addProperty("link", "testLink")
            addProperty("priority", 1)
            addProperty("_rev", "testRev")
            addProperty("status", "read")
            addProperty("time", 123456789L)
        }
        coEvery { notificationDao.getById("testId") } returns null
        val upsertSlot = slot<AppNotification>()
        coEvery { notificationDao.upsert(capture(upsertSlot)) } returns Unit

        repository.insert(jsonObject)

        val savedNotification = upsertSlot.captured
        assertEquals("testUser", savedNotification.userId)
        assertEquals("testMessage", savedNotification.message)
        assertEquals("testType", savedNotification.type)
        assertEquals("testLink", savedNotification.link)
        assertEquals(1, savedNotification.priority)
        assertEquals("testRev", savedNotification.rev)
        assertTrue(savedNotification.isRead)
        assertEquals(123456789L, savedNotification.createdAt.time)
        assertTrue(savedNotification.isFromServer)
    }

    @Test
    fun `insert updates existing notification`() = runTest {
        val existing = AppNotification().apply { id = "testId" }
        val jsonObject = JsonObject().apply {
            addProperty("_id", "testId")
            addProperty("user", "updatedUser")
            addProperty("message", "updatedMessage")
            addProperty("type", "updatedType")
            addProperty("status", "unread")
            addProperty("time", 987654321L)
        }
        coEvery { notificationDao.getById("testId") } returns existing
        val upsertSlot = slot<AppNotification>()
        coEvery { notificationDao.upsert(capture(upsertSlot)) } returns Unit

        repository.insert(jsonObject)

        val savedNotification = upsertSlot.captured
        assertEquals("updatedUser", savedNotification.userId)
        assertEquals("updatedMessage", savedNotification.message)
        assertEquals("updatedType", savedNotification.type)
        assertFalse(savedNotification.isRead)
        assertEquals(987654321L, savedNotification.createdAt.time)
        assertTrue(savedNotification.isFromServer)
    }

    @Test
    fun `insert preserves read status if needsSync is true`() = runTest {
        val existing = AppNotification().apply {
            id = "testId"
            isRead = true
            needsSync = true
        }
        val jsonObject = JsonObject().apply {
            addProperty("_id", "testId")
            addProperty("status", "unread")
        }
        coEvery { notificationDao.getById("testId") } returns existing
        val upsertSlot = slot<AppNotification>()
        coEvery { notificationDao.upsert(capture(upsertSlot)) } returns Unit

        repository.insert(jsonObject)

        assertTrue(upsertSlot.captured.isRead)
    }

    @Test
    fun `insert keeps raw team type and extracts team id from item for join requests`() = runTest {
        val jsonObject = JsonObject().apply {
            addProperty("_id", "notifId1")
            addProperty("user", "testUser")
            addProperty("message", "<b>Jane</b> has requested to join <b>\"My Team\"</b> team.")
            addProperty("type", "team")
            addProperty("item", "team123")
            add("linkParams", JsonObject().apply { addProperty("activeTab", "applicantTab") })
            addProperty("status", "unread")
        }
        coEvery { notificationDao.getById("notifId1") } returns null
        val upsertSlot = slot<AppNotification>()
        coEvery { notificationDao.upsert(capture(upsertSlot)) } returns Unit

        repository.insert(jsonObject)

        val saved = upsertSlot.captured
        assertEquals("team", saved.type)
        assertEquals("<b>Jane</b> has requested to join <b>\"My Team\"</b> team.", saved.message)
        assertEquals("team123", saved.relatedId)
        assertEquals("join_request", saved.subType)
    }

    @Test
    fun `insert keeps raw team type and extracts team id from item for team updates`() = runTest {
        val jsonObject = JsonObject().apply {
            addProperty("_id", "notifId2")
            addProperty("user", "testUser")
            addProperty("message", "You have been added to <b>\"My Team\"</b> team.")
            addProperty("type", "team")
            addProperty("item", "team456")
            addProperty("status", "unread")
        }
        coEvery { notificationDao.getById("notifId2") } returns null
        val upsertSlot = slot<AppNotification>()
        coEvery { notificationDao.upsert(capture(upsertSlot)) } returns Unit

        repository.insert(jsonObject)

        val saved = upsertSlot.captured
        assertEquals("team", saved.type)
        assertEquals("team456", saved.relatedId)
        assertEquals(null, saved.subType)
    }

    @Test
    fun `insert keeps raw replyMessage type and extracts news id from replyTo`() = runTest {
        val jsonObject = JsonObject().apply {
            addProperty("_id", "notifId3")
            addProperty("user", "testUser")
            addProperty("message", "<b>Jane</b> replied to your message.")
            addProperty("type", "replyMessage")
            addProperty("replyTo", "news789")
            addProperty("status", "unread")
        }
        coEvery { notificationDao.getById("notifId3") } returns null
        val upsertSlot = slot<AppNotification>()
        coEvery { notificationDao.upsert(capture(upsertSlot)) } returns Unit

        repository.insert(jsonObject)

        val saved = upsertSlot.captured
        assertEquals("replyMessage", saved.type)
        assertEquals("news789", saved.relatedId)
    }

    @Test
    fun `insert keeps raw newTask type and extracts team id from link`() = runTest {
        val jsonObject = JsonObject().apply {
            addProperty("_id", "notifId4")
            addProperty("user", "testUser")
            addProperty("message", "You were assigned a new task")
            addProperty("type", "newTask")
            addProperty("link", "/teams/view/team321")
            addProperty("status", "unread")
        }
        coEvery { notificationDao.getById("notifId4") } returns null
        val upsertSlot = slot<AppNotification>()
        coEvery { notificationDao.upsert(capture(upsertSlot)) } returns Unit

        repository.insert(jsonObject)

        val saved = upsertSlot.captured
        assertEquals("newTask", saved.type)
        assertEquals("team321", saved.relatedId)
    }

    @Test
    fun `insert leaves unrecognized types unchanged with no relatedId`() = runTest {
        val jsonObject = JsonObject().apply {
            addProperty("_id", "notifId5")
            addProperty("user", "testUser")
            addProperty("message", "New resource notification")
            addProperty("type", "newResource")
            addProperty("link", "/resources")
            addProperty("status", "unread")
        }
        coEvery { notificationDao.getById("notifId5") } returns null
        val upsertSlot = slot<AppNotification>()
        coEvery { notificationDao.upsert(capture(upsertSlot)) } returns Unit

        repository.insert(jsonObject)

        val saved = upsertSlot.captured
        assertEquals("newResource", saved.type)
        assertEquals(null, saved.relatedId)
    }

    @Test
    fun `getTaskDetails resolves team via task link when task exists`() = runTest {
        val task = org.ole.planet.myplanet.model.TeamTask().apply {
            id = "task1"
            link = "{\"teams\":\"team321\"}"
        }
        coEvery { teamTaskDao.getById("task1") } returns task
        coEvery { teamsRepository.get().getTeamLabelInfo("team321") } returns TeamLabelInfo("team321", "My Team", "team")

        val result = repository.getTaskDetails("task1")

        assertEquals("team321", result?.teamId)
        assertEquals("My Team", result?.teamName)
    }

    @Test
    fun `getTaskDetails returns null when relatedId is not a known task id`() = runTest {
        coEvery { teamTaskDao.getById("team321") } returns null

        val result = repository.getTaskDetails("team321")

        assertEquals(null, result)
    }

    @Test
    fun `getJoinRequestTeamId strips join_request_ prefix and resolves team id`() = runTest {
        coEvery { teamsRepository.get().getJoinRequestInfo("req1") } returns JoinRequestInfo("req1", "team9", "user1")

        val result = repository.getJoinRequestTeamId("join_request_req1")

        assertEquals("team9", result)
    }

    @Test
    fun `getJoinRequestTeamId returns null when relatedId is not a known join request id`() = runTest {
        coEvery { teamsRepository.get().getJoinRequestInfo("team123") } returns null

        val result = repository.getJoinRequestTeamId("team123")

        assertEquals(null, result)
    }

    @Test
    fun `refresh does nothing`() = runTest {
        repository.refresh()
        // No exceptions, does nothing
    }

    @Test
    fun `markNotificationAsRead marks summary as read when id starts with summary_`() = runTest {
        coEvery { notificationDao.markSummaryAsRead(any(), any()) } returns 1

        repository.markNotificationAsRead("summary_testType", "user1")

        coVerify { notificationDao.markSummaryAsRead("user1", "testType") }
        coVerify(exactly = 0) { notificationDao.markAsRead(any()) }
    }

    @Test
    fun `markNotificationAsRead marks regular notification as read`() = runTest {
        coEvery { notificationDao.markAsRead(any()) } returns 1

        repository.markNotificationAsRead("regular_id", "user1")

        coVerify { notificationDao.markAsRead("regular_id") }
        coVerify(exactly = 0) { notificationDao.markSummaryAsRead(any(), any()) }
    }

    @Test
    fun `getPendingSyncNotifications returns notifications from dao`() = runTest {
        val list = listOf(AppNotification())
        coEvery { notificationDao.getPendingSyncNotifications() } returns list

        val result = repository.getPendingSyncNotifications()

        assertEquals(list, result)
        coVerify { notificationDao.getPendingSyncNotifications() }
    }

    @Test
    fun `markNotificationsSynced marks all as synced`() = runTest {
        coEvery { notificationDao.markSynced(any<List<Pair<String, String?>>>()) } returns Unit
        val syncResults = listOf(Pair("id1", "rev1"), Pair("id2", "rev2"))

        repository.markNotificationsSynced(syncResults)

        coVerify { notificationDao.markSynced(syncResults) }
    }

    @Test
    fun `markNotificationsSynced with empty list does nothing`() = runTest {
        repository.markNotificationsSynced(emptyList())

        coVerify(exactly = 0) { notificationDao.markSynced(any<List<Pair<String, String?>>>()) }
    }

    @Test
    fun `markNotificationsAsRead with empty set does nothing`() = runTest {
        val result = repository.markNotificationsAsRead(emptySet())

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { notificationDao.getIdsByIds(any()) }
        coVerify(exactly = 0) { notificationDao.markAsRead(any<List<String>>(), any()) }
    }

    @Test
    fun `markNotificationsAsRead marks existing notifications as read and returns ids`() = runTest {
        val ids = setOf("id1", "id2", "id3")
        coEvery { notificationDao.getIdsByIds(any()) } returns listOf("id1", "id2")
        coEvery { notificationDao.markAsRead(any<List<String>>(), any()) } returns 2

        val result = repository.markNotificationsAsRead(ids)

        assertEquals(setOf("id1", "id2"), result)
        coVerify { notificationDao.getIdsByIds(ids.toList()) }
        coVerify { notificationDao.markAsRead(listOf("id1", "id2"), any()) }
    }

    @Test
    fun `markAllUnreadAsRead with null userId returns empty set`() = runTest {
        val result = repository.markAllUnreadAsRead(null)

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { notificationDao.getUnreadIds(any()) }
        coVerify(exactly = 0) { notificationDao.markAllUnreadAsRead(any(), any()) }
    }

    @Test
    fun `markAllUnreadAsRead fetches unread ids and updates all unread`() = runTest {
        coEvery { notificationDao.getUnreadIds("user1") } returns listOf("id1", "id2")
        coEvery { notificationDao.markAllUnreadAsRead("user1", any()) } returns 2

        val result = repository.markAllUnreadAsRead("user1")

        assertEquals(setOf("id1", "id2"), result)
        coVerify { notificationDao.getUnreadIds("user1") }
        coVerify { notificationDao.markAllUnreadAsRead("user1", any()) }
    }

    @Test
    fun `deleteNotifications with empty set does nothing`() = runTest {
        val result = repository.deleteNotifications(emptySet())

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { notificationDao.getIdsByIds(any()) }
        coVerify(exactly = 0) { notificationDao.deleteByIds(any()) }
    }

    @Test
    fun `deleteNotifications deletes existing notifications and returns deleted ids`() = runTest {
        val ids = setOf("id1", "id2", "id3")
        coEvery { notificationDao.getIdsByIds(any()) } returns listOf("id1", "id2")
        coEvery { notificationDao.deleteByIds(any()) } returns 2

        val result = repository.deleteNotifications(ids)

        assertEquals(setOf("id1", "id2"), result)
        coVerify { notificationDao.getIdsByIds(ids.toList()) }
        coVerify { notificationDao.deleteByIds(listOf("id1", "id2")) }
    }

    @Test
    fun `bulkInsertFromSync inserts parsed notifications`() = runTest {
        val jsonArray = JsonArray()

        val doc1 = JsonObject().apply {
            add("doc", JsonObject().apply {
                addProperty("_id", "testId1")
                addProperty("user", "user1")
                addProperty("message", "msg1")
            })
        }
        val doc2 = JsonObject().apply {
            add("doc", JsonObject().apply {
                addProperty("_id", "_design/something")
                addProperty("user", "user2")
            })
        }
        val doc3 = JsonObject().apply {
            add("doc", JsonObject().apply {
                addProperty("_id", "testId2")
                addProperty("user", "user3")
                addProperty("message", "msg3")
            })
        }
        jsonArray.add(doc1)
        jsonArray.add(doc2)
        jsonArray.add(doc3)

        val existingNotification = AppNotification().apply {
            id = "testId2"
            needsSync = true
            isRead = true
        }
        coEvery { notificationDao.getByIds(any()) } returns listOf(existingNotification)

        val upsertSlot = slot<List<AppNotification>>()
        coEvery { notificationDao.upsertAll(capture(upsertSlot)) } returns Unit

        repository.bulkInsertFromSync(jsonArray)

        val saved = upsertSlot.captured
        assertEquals(2, saved.size)

        val first = saved.find { it.id == "testId1" }!!
        assertEquals("user1", first.userId)
        assertEquals("msg1", first.message)
        assertFalse(first.needsSync)
        assertTrue(first.isRead) // parsed from missing 'status' which doesn't equal 'unread'

        val second = saved.find { it.id == "testId2" }!!
        assertEquals("user3", second.userId)
        assertEquals("msg3", second.message)
        assertTrue(second.needsSync)
        assertTrue(second.isRead)
    }

    @Test
    fun `bulkInsertFromSync with empty array does nothing`() = runTest {
        val jsonArray = JsonArray()

        val upsertSlot = slot<List<AppNotification>>()
        coEvery { notificationDao.upsertAll(capture(upsertSlot)) } returns Unit

        repository.bulkInsertFromSync(jsonArray)

        val saved = upsertSlot.captured
        assertTrue(saved.isEmpty())
        coVerify(exactly = 0) { notificationDao.getByIds(any()) }
    }

    @Test
    fun `getEnrichedNotifications classifies task and join_request buckets and performs fan-out`() = runTest {
        val task1 = AppNotification().apply {
            id = "t1"
            userId = "user1"
            type = "task"
            message = "Task 1 Mon 12, Jan 2024"
            relatedId = "rel1"
        }
        val join1 = AppNotification().apply {
            id = "j1"
            userId = "user1"
            type = "join_request"
            message = "Join 1"
            relatedId = "rel2"
        }
        val joinNoId = AppNotification().apply {
            id = "j2"
            userId = "user1"
            type = "join_request"
            message = "Join No ID"
            relatedId = null
        }

        coEvery { notificationDao.getNotifications("user1", "", false) } returns listOf(task1, join1, joinNoId)
        coEvery { notificationDao.getUnreadCount("user1", false) } returns 3

        val taskEntity = org.ole.planet.myplanet.model.TeamTask().apply {
            id = "rel1"
            title = "Task 1"
            teamId = "teamA"
        }
        coEvery { teamTaskDao.getByIds(listOf("rel1")) } returns listOf(taskEntity)
        coEvery { teamTaskDao.getByTitles(listOf("Task 1")) } returns listOf(taskEntity)
        coEvery { teamsRepository.get().getTeamNamesByIds(listOf("teamA")) } returns mapOf("teamA" to "Alpha Team")

        val joinRequestInfo = JoinRequestInfo("rel2", "teamB", "user2")
        val fallbackRequestInfo = JoinRequestInfo("fallback_id", "teamC", "user3")
        coEvery { teamsRepository.get().getJoinRequestsInfo(listOf("rel2")) } returns listOf(joinRequestInfo)
        coEvery { teamsRepository.get().getJoinRequestInfo(null) } returns fallbackRequestInfo
        coEvery { teamsRepository.get().getTeamNamesByIds(listOf("teamB")) } returns mapOf("teamB" to "Beta Team")
        coEvery { teamsRepository.get().getTeamLabelInfo("teamC") } returns TeamLabelInfo("teamC", "Gamma Team", "team")

        coEvery { userRepository.get().getUsersByIds(listOf("user2")) } returns listOf(
            org.ole.planet.myplanet.model.UserEntity(id = "user2", name = "Alice")
        )
        coEvery { userRepository.get().getUserById("user3") } returns org.ole.planet.myplanet.model.UserEntity(id = "user3", name = "Bob")

        val enrichment = repository.getEnrichedNotifications("user1", "all", false)

        assertEquals(3, enrichment.payloads.size)
        assertEquals(3, enrichment.unreadCount)

        assertEquals("Alpha Team", enrichment.taskTeamNames["rel1"])
        assertEquals(Pair("Alice", "Beta Team"), enrichment.joinRequestDetails["rel2"])
        assertEquals(Pair("Bob", "Gamma Team"), enrichment.joinRequestDetails[""])

        coVerify { teamTaskDao.getByIds(listOf("rel1")) }
        coVerify { teamTaskDao.getByTitles(listOf("Task 1")) }
        coVerify { teamsRepository.get().getJoinRequestsInfo(listOf("rel2")) }
        coVerify { teamsRepository.get().getJoinRequestInfo(null) }
        coVerify { notificationDao.getUnreadCount("user1", false) }
    }

    @Test
    fun `getEnrichedNotifications task id precedence over task title precedence`() = runTest {
        val task1 = AppNotification().apply {
            id = "t1"
            userId = "user1"
            type = "task"
            message = "Task 1 Mon 12, Jan 2024"
            relatedId = "rel1"
        }
        coEvery { notificationDao.getNotifications("user1", "", false) } returns listOf(task1)
        coEvery { notificationDao.getUnreadCount("user1", false) } returns 1

        val taskById = org.ole.planet.myplanet.model.TeamTask().apply {
            id = "rel1"
            title = "Task 1"
            teamId = "teamIdWinner"
        }
        val taskByTitle = org.ole.planet.myplanet.model.TeamTask().apply {
            id = "other_id"
            title = "Task 1"
            teamId = "teamTitleLoser"
        }

        coEvery { teamTaskDao.getByIds(listOf("rel1")) } returns listOf(taskById)
        coEvery { teamTaskDao.getByTitles(listOf("Task 1")) } returns listOf(taskByTitle)
        coEvery { teamsRepository.get().getTeamNamesByIds(listOf("teamIdWinner")) } returns mapOf("teamIdWinner" to "Winner Team")
        coEvery { teamsRepository.get().getTeamNamesByIds(listOf("teamTitleLoser")) } returns mapOf("teamTitleLoser" to "Loser Team")

        val enrichment = repository.getEnrichedNotifications("user1", "all", false)

        // Title produces ("Task 1" -> "Loser Team"), but ID produces ("rel1" -> "Winner Team").
        // Since putAll(ids) is called second, ids win.
        assertEquals("Winner Team", enrichment.taskTeamNames["rel1"])
        assertEquals("Loser Team", enrichment.taskTeamNames["Task 1"])
    }

    @Test
    fun `getEnrichedNotifications does not query titles when taskTitles is empty`() = runTest {
        val taskDateless = AppNotification().apply {
            id = "t1"
            userId = "user1"
            type = "task"
            message = "Task without date"
            relatedId = "rel1"
        }
        coEvery { notificationDao.getNotifications("user1", "", false) } returns listOf(taskDateless)
        coEvery { notificationDao.getUnreadCount("user1", false) } returns 1

        val taskById = org.ole.planet.myplanet.model.TeamTask().apply {
            id = "rel1"
            title = "Task without date"
            teamId = "teamA"
        }
        coEvery { teamTaskDao.getByIds(listOf("rel1")) } returns listOf(taskById)
        coEvery { teamsRepository.get().getTeamNamesByIds(listOf("teamA")) } returns mapOf("teamA" to "Alpha Team")

        repository.getEnrichedNotifications("user1", "all", false)

        coVerify(exactly = 0) { teamTaskDao.getByTitles(any()) }
    }

    @Test
    fun `resolveType passes through known types lowercased`() {
        assertEquals("join_request", repository.resolveType("join_request", "anything", null))
        assertEquals("task", repository.resolveType("Task", "anything", null))
        assertEquals("resource", repository.resolveType("RESOURCE", "anything", null))
    }

    @Test
    fun `resolveType classifies raw team type as join request via english message`() {
        assertEquals(
            "join_request",
            repository.resolveType("team", "<b>Jane</b> has requested to join <b>\"My Team\"</b> team.", null)
        )
    }

    @Test
    fun `resolveType classifies raw team type as join request via spanish message`() {
        assertEquals(
            "join_request",
            repository.resolveType("team", "test22012601 ha solicitado unirse a \"test GT\" team.", null)
        )
    }

    @Test
    fun `resolveType classifies raw team type as join request via subType regardless of message language`() {
        assertEquals("join_request", repository.resolveType("team", "غير معروف", "join_request"))
    }

    @Test
    fun `resolveType lowercases subType`() {
        assertEquals("join_request", repository.resolveType("team", "غير معروف", "Join_Request"))
    }

    @Test
    fun `resolveType classifies raw team type case insensitively`() {
        assertEquals("team_join", repository.resolveType("Team", "Has sido eliminado de \"test GT\" team.", null))
    }

    @Test
    fun `resolveType classifies raw newTask and newResource types case insensitively`() {
        assertEquals("task", repository.resolveType("NEWTASK", "¿qué?", null))
        assertEquals("resource", repository.resolveType("newresource", "¿qué?", null))
    }

    @Test
    fun `resolveType classifies raw team type as chat for posted message`() {
        assertEquals(
            "chat",
            repository.resolveType("team", "Bhushan Nim has posted a message on \"test GT\" team.", null)
        )
    }

    @Test
    fun `resolveType classifies unmatched raw team type as team join`() {
        assertEquals(
            "team_join",
            repository.resolveType("team", "Has sido eliminado de \"test GT\" team.", null)
        )
    }

    @Test
    fun `resolveType classifies newTask as task`() {
        assertEquals("task", repository.resolveType("newTask", "You were assigned a new task", null))
    }

    @Test
    fun `resolveType classifies newResource as resource`() {
        assertEquals("resource", repository.resolveType("newResource", "Hay nuevos recursos en la biblioteca.", null))
    }

    @Test
    fun `getTeamNotifications attributes task badge only to the team owning the due task`() = runTest {
        val team1 = "team1"
        val team2 = "team2"
        val teamIds = listOf(team1, team2)
        val userId = "user1"

        val taskForTeam1 = org.ole.planet.myplanet.model.TeamTask().apply {
            id = "task1"
            teamId = team1
        }

        coEvery { teamNotificationDao.getByTypeAndParentIds("chat", teamIds) } returns emptyList()
        coEvery { voicesRepository.countTopLevelByTeams(emptyList()) } returns emptyMap()
        coEvery { teamTaskDao.getTasksForUserBetween(eq(userId), any(), any()) } returns listOf(taskForTeam1)

        val result = repository.getTeamNotifications(teamIds, userId)

        assertEquals(2, result.size)
        assertTrue(result[team1]?.hasTask == true)
        assertFalse(result[team2]?.hasTask == true)
    }

    @Test
    fun `getTeamNotifications only counts messages for teams with chat notification row`() = runTest {
        val chatTrackedTeamId = "teamTracked"
        val untrackedTeamId = "teamUntracked"
        val teamIds = listOf(chatTrackedTeamId, untrackedTeamId)
        val userId = "user1"

        val chatNotification = TeamNotification().apply {
            parentId = chatTrackedTeamId
            type = "chat"
            lastCount = 2
        }

        coEvery { teamNotificationDao.getByTypeAndParentIds("chat", teamIds) } returns listOf(chatNotification)
        coEvery { voicesRepository.countTopLevelByTeams(listOf(chatTrackedTeamId)) } returns mapOf(chatTrackedTeamId to 5L)
        coEvery { teamTaskDao.getTasksForUserBetween(eq(userId), any(), any()) } returns emptyList()

        val result = repository.getTeamNotifications(teamIds, userId)

        coVerify(exactly = 1) { voicesRepository.countTopLevelByTeams(listOf(chatTrackedTeamId)) }

        assertEquals(2, result.size)
        assertTrue(result[chatTrackedTeamId]?.hasChat == true)
        assertFalse(result[chatTrackedTeamId]?.hasTask == true)
        assertFalse(result[untrackedTeamId]?.hasChat == true)
        assertFalse(result[untrackedTeamId]?.hasTask == true)
    }

    @Test
    fun `getTeamNotifications returns empty map when teamIds is empty`() = runTest {
        val result = repository.getTeamNotifications(emptyList(), "user1")
        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { teamNotificationDao.getByTypeAndParentIds(any(), any()) }
        coVerify(exactly = 0) { voicesRepository.countTopLevelByTeams(any()) }
    }

    @Test
    fun `getTeamNotifications fetches chat counts for multiple tracked teams in a single batched call`() = runTest {
        val team1 = "team1"
        val team2 = "team2"
        val team3 = "team3"
        val teamIds = listOf(team1, team2, team3)
        val userId = "user1"

        val notif1 = TeamNotification().apply { parentId = team1; type = "chat"; lastCount = 2 }
        val notif2 = TeamNotification().apply { parentId = team2; type = "chat"; lastCount = 5 }
        val notif3 = TeamNotification().apply { parentId = team3; type = "chat"; lastCount = 10 }

        coEvery { teamNotificationDao.getByTypeAndParentIds("chat", teamIds) } returns listOf(notif1, notif2, notif3)
        coEvery { voicesRepository.countTopLevelByTeams(teamIds) } returns mapOf(team1 to 5L, team2 to 3L, team3 to 10L)
        coEvery { teamTaskDao.getTasksForUserBetween(eq(userId), any(), any()) } returns emptyList()

        val result = repository.getTeamNotifications(teamIds, userId)

        coVerify(exactly = 1) { voicesRepository.countTopLevelByTeams(teamIds) }

        assertEquals(3, result.size)
        assertTrue(result[team1]?.hasChat == true)   // 2 < 5
        assertFalse(result[team2]?.hasChat == true)  // 5 >= 3
        assertFalse(result[team3]?.hasChat == true)  // 10 >= 10
    }

    @Test
    fun `resolveType falls back to message sniffing for unknown types`() {
        assertEquals("task", repository.resolveType("other", "Report is due tomorrow", null))
        assertEquals("storage", repository.resolveType("other", "Low storage", null))
        assertEquals("voice_reply", repository.resolveType("other", "new reply to your voice", null))
        assertEquals("notification", repository.resolveType("other", "unrecognized text", null))
    }

    @Test
    fun `updateResourceNotification returns early when userId is null`() = runTest {
        repository.updateResourceNotification(null, 5)

        coVerify(exactly = 0) { notificationDao.getById(any()) }
        coVerify(exactly = 0) { notificationDao.upsert(any()) }
        coVerify(exactly = 0) { notificationDao.deleteById(any()) }
    }

    @Test
    fun `updateResourceNotification creates new notification on first run`() = runTest {
        coEvery { notificationDao.getById("user1:resource:count") } returns null
        val upsertSlot = slot<AppNotification>()
        coEvery { notificationDao.upsert(capture(upsertSlot)) } returns Unit

        repository.updateResourceNotification("user1", 5)

        val saved = upsertSlot.captured
        assertEquals("user1:resource:count", saved.id)
        assertEquals("user1", saved.userId)
        assertEquals("resource", saved.type)
        assertEquals("5", saved.message)
        assertEquals("5", saved.relatedId)
        assertFalse(saved.isRead)
    }

    @Test
    fun `updateResourceNotification when count unchanged keeps it read`() = runTest {
        val initialDate = java.util.Date(1000000L)
        val existing = AppNotification().apply {
            id = "user1:resource:count"
            userId = "user1"
            type = "resource"
            message = "5"
            relatedId = "5"
            isRead = true
            createdAt = initialDate
        }
        coEvery { notificationDao.getById("user1:resource:count") } returns existing
        val upsertSlot = slot<AppNotification>()
        coEvery { notificationDao.upsert(capture(upsertSlot)) } returns Unit

        repository.updateResourceNotification("user1", 5)

        val saved = upsertSlot.captured
        assertTrue(saved.isRead)
        assertEquals(initialDate, saved.createdAt)
        assertEquals("5", saved.message)
    }

    @Test
    fun `updateResourceNotification when count changed marks unread and updates createdAt`() = runTest {
        val initialDate = java.util.Date(1000000L)
        val existing = AppNotification().apply {
            id = "user1:resource:count"
            userId = "user1"
            type = "resource"
            message = "5"
            relatedId = "5"
            isRead = true
            createdAt = initialDate
        }
        coEvery { notificationDao.getById("user1:resource:count") } returns existing
        val upsertSlot = slot<AppNotification>()
        coEvery { notificationDao.upsert(capture(upsertSlot)) } returns Unit

        repository.updateResourceNotification("user1", 10)

        val saved = upsertSlot.captured
        assertFalse(saved.isRead)
        assertEquals("10", saved.message)
        assertEquals("10", saved.relatedId)
        assertTrue(saved.createdAt.after(initialDate))
    }

    @Test
    fun `updateResourceNotification when count is zero or negative deletes existing notification`() = runTest {
        val existing = AppNotification().apply {
            id = "user1:resource:count"
        }
        coEvery { notificationDao.getById("user1:resource:count") } returns existing

        repository.updateResourceNotification("user1", 0)

        coVerify { notificationDao.deleteById("user1:resource:count") }
        coVerify(exactly = 0) { notificationDao.upsert(any()) }
    }

    @Test
    fun `updateStorageNotification returns early when userId is null`() = runTest {
        repository.updateStorageNotification(null, 5)

        coVerify(exactly = 0) { notificationDao.getById(any()) }
        coVerify(exactly = 0) { notificationDao.upsert(any()) }
        coVerify(exactly = 0) { notificationDao.deleteById(any()) }
    }

    @Test
    fun `updateStorageNotification creates new notification on first run`() = runTest {
        coEvery { notificationDao.getById("user1:storage") } returns null
        val upsertSlot = slot<AppNotification>()
        coEvery { notificationDao.upsert(capture(upsertSlot)) } returns Unit

        repository.updateStorageNotification("user1", 8)

        val saved = upsertSlot.captured
        assertEquals("user1:storage", saved.id)
        assertEquals("user1", saved.userId)
        assertEquals("storage", saved.type)
        assertEquals("8%", saved.message)
        assertEquals("storage", saved.relatedId)
        assertFalse(saved.isRead)
    }

    @Test
    fun `updateStorageNotification when percent unchanged keeps it read`() = runTest {
        val initialDate = java.util.Date(1000000L)
        val existing = AppNotification().apply {
            id = "user1:storage"
            userId = "user1"
            type = "storage"
            message = "8%"
            relatedId = "storage"
            isRead = true
            createdAt = initialDate
        }
        coEvery { notificationDao.getById("user1:storage") } returns existing
        val upsertSlot = slot<AppNotification>()
        coEvery { notificationDao.upsert(capture(upsertSlot)) } returns Unit

        repository.updateStorageNotification("user1", 8)

        val saved = upsertSlot.captured
        assertTrue(saved.isRead)
        assertEquals(initialDate, saved.createdAt)
        assertEquals("8%", saved.message)
    }

    @Test
    fun `updateStorageNotification when percent changed marks unread and updates createdAt`() = runTest {
        val initialDate = java.util.Date(1000000L)
        val existing = AppNotification().apply {
            id = "user1:storage"
            userId = "user1"
            type = "storage"
            message = "8%"
            relatedId = "storage"
            isRead = true
            createdAt = initialDate
        }
        coEvery { notificationDao.getById("user1:storage") } returns existing
        val upsertSlot = slot<AppNotification>()
        coEvery { notificationDao.upsert(capture(upsertSlot)) } returns Unit

        repository.updateStorageNotification("user1", 5)

        val saved = upsertSlot.captured
        assertFalse(saved.isRead)
        assertEquals("5%", saved.message)
        assertEquals("storage", saved.relatedId)
        assertTrue(saved.createdAt.after(initialDate))
    }

    @Test
    fun `updateStorageNotification when percent exceeds threshold deletes existing notification`() = runTest {
        val existing = AppNotification().apply {
            id = "user1:storage"
        }
        coEvery { notificationDao.getById("user1:storage") } returns existing

        repository.updateStorageNotification("user1", 15)

        coVerify { notificationDao.deleteById("user1:storage") }
        coVerify(exactly = 0) { notificationDao.upsert(any()) }
    }

    @Test
    fun `updateTeamNotification creates new team notification using news list size`() = runTest {
        val teamId = "team123"
        val news = listOf(
            org.ole.planet.myplanet.model.News(),
            org.ole.planet.myplanet.model.News()
        )
        coEvery { teamNotificationDao.updateCount(teamId, "chat", 2) } returns 0
        val slot = slot<TeamNotification>()
        coEvery { teamNotificationDao.insert(capture(slot)) } returns Unit

        repository.updateTeamNotification(teamId, news)

        val inserted = slot.captured
        assertEquals(teamId, inserted.parentId)
        assertEquals("chat", inserted.type)
        assertEquals(2, inserted.lastCount)
    }

    @Test
    fun `updateTeamNotification updates existing team notification using news list size`() = runTest {
        val teamId = "team123"
        val news = listOf(
            org.ole.planet.myplanet.model.News(),
            org.ole.planet.myplanet.model.News(),
            org.ole.planet.myplanet.model.News()
        )
        coEvery { teamNotificationDao.updateCount(teamId, "chat", 3) } returns 1

        repository.updateTeamNotification(teamId, news)

        coVerify(exactly = 0) { teamNotificationDao.insert(any()) }
    }
}
