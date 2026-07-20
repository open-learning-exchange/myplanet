package org.ole.planet.myplanet.repository

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
import org.ole.planet.myplanet.data.room.dao.NewsDao
import org.ole.planet.myplanet.data.room.dao.NotificationDao
import org.ole.planet.myplanet.data.room.dao.TeamNotificationDao
import org.ole.planet.myplanet.data.room.dao.TeamTaskDao
import org.ole.planet.myplanet.data.room.dao.ExamDao
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
    private lateinit var newsDao: NewsDao
    private lateinit var examDao: ExamDao

    @Before
    fun setUp() {
        userRepository = mockk(relaxed = true)
        teamsRepository = mockk(relaxed = true)
        teamNotificationDao = mockk(relaxed = true)
        notificationDao = mockk(relaxed = true)
        teamTaskDao = mockk(relaxed = true)
        newsDao = mockk(relaxed = true)
        examDao = mockk(relaxed = true)
        repository = NotificationsRepositoryImpl(
            userRepository,
            teamsRepository,
            TestTimeProvider(),
            teamNotificationDao,
            notificationDao,
            teamTaskDao,
            newsDao,
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
}
