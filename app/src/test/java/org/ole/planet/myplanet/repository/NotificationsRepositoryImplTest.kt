package org.ole.planet.myplanet.repository

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
import org.ole.planet.myplanet.data.room.dao.ExamDao
import org.ole.planet.myplanet.data.room.dao.NotificationDao
import org.ole.planet.myplanet.data.room.dao.TeamNotificationDao
import org.ole.planet.myplanet.data.room.dao.TeamTaskDao
import org.ole.planet.myplanet.model.AppNotification
import org.ole.planet.myplanet.utils.TestTimeProvider

@ExperimentalCoroutinesApi
class NotificationsRepositoryImplTest {

    private lateinit var userRepository: dagger.Lazy<UserRepository>
    private lateinit var teamsRepository: dagger.Lazy<TeamsRepository>
    private lateinit var repository: NotificationsRepositoryImpl
    private lateinit var teamNotificationDao: TeamNotificationDao
    private lateinit var notificationDao: NotificationDao
    private lateinit var teamTaskDao: TeamTaskDao
    private lateinit var voicesRepository: VoicesRepository
    private lateinit var examDao: ExamDao

    @Before
    fun setUp() {
        userRepository = mockk(relaxed = true)
        teamsRepository = mockk(relaxed = true)
        teamNotificationDao = mockk(relaxed = true)
        notificationDao = mockk(relaxed = true)
        teamTaskDao = mockk(relaxed = true)
        voicesRepository = mockk(relaxed = true)
        examDao = mockk(relaxed = true)
        repository = NotificationsRepositoryImpl(
            userRepository,
            teamsRepository,
            TestTimeProvider(),
            teamNotificationDao,
            notificationDao,
            teamTaskDao,
            voicesRepository,
            examDao,
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
        coEvery { notificationDao.markSynced(any(), any()) } returns Unit
        val syncResults = listOf(Pair("id1", "rev1"), Pair("id2", "rev2"))

        repository.markNotificationsSynced(syncResults)

        coVerify { notificationDao.markSynced("id1", "rev1") }
        coVerify { notificationDao.markSynced("id2", "rev2") }
    }

    @Test
    fun `markNotificationsSynced with empty list does nothing`() = runTest {
        repository.markNotificationsSynced(emptyList())

        coVerify(exactly = 0) { notificationDao.markSynced(any(), any()) }
    }

    @Test
    fun `deleteNotifications with empty set does nothing`() = runTest {
        val result = repository.deleteNotifications(emptySet())

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { notificationDao.getByIds(any()) }
        coVerify(exactly = 0) { notificationDao.deleteByIds(any()) }
    }

    @Test
    fun `deleteNotifications deletes existing notifications and returns deleted ids`() = runTest {
        val ids = setOf("id1", "id2", "id3")
        val notifications = listOf(
            AppNotification().apply { id = "id1" },
            AppNotification().apply { id = "id2" }
        )
        coEvery { notificationDao.getByIds(any()) } returns notifications
        coEvery { notificationDao.deleteByIds(any()) } returns 2

        val result = repository.deleteNotifications(ids)

        assertEquals(setOf("id1", "id2"), result)
        coVerify { notificationDao.getByIds(ids.toList()) }
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
}
