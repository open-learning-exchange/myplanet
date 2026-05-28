package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.every
import io.mockk.invoke
import io.mockk.mockk
import io.mockk.mockkConstructor
import org.json.JSONObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmNotification

import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.data.findCopyByField

@ExperimentalCoroutinesApi
class NotificationsRepositoryImplTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var userRepository: dagger.Lazy<UserRepository>
    private lateinit var repository: NotificationsRepositoryImpl
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        io.mockk.mockkStatic(io.realm.Realm::class)
        every { io.realm.Realm.getDefaultInstance() } returns mockk(relaxed = true)
        io.mockk.mockkStatic(io.realm.log.RealmLog::class)
        every { io.realm.log.RealmLog.error(any<Throwable>(), any()) } returns Unit
        every { io.realm.log.RealmLog.error(any<String>()) } returns Unit

        databaseService = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        repository = NotificationsRepositoryImpl(databaseService, testDispatcher, userRepository)
    }


    @Test
    fun `insert with missing id does nothing`() = runTest {
        val realm = mockk<Realm>(relaxed = true)
        val jsonObject = JsonObject() // Missing _id

        val transactionSlot = slot<(Realm) -> Unit>()
        coEvery { databaseService.executeTransactionAsync(capture(transactionSlot)) } answers {
            transactionSlot.captured.invoke(realm)
        }

        repository.insert(jsonObject)

        verify(exactly = 0) { realm.where(RealmNotification::class.java) }
        verify(exactly = 0) { realm.createObject(RealmNotification::class.java, any<String>()) }
    }

    @Test
    fun `insert creates new notification when not found`() = runTest {
        val realm = mockk<Realm>(relaxed = true)
        val query = mockk<RealmQuery<RealmNotification>>()
        val notification = RealmNotification()
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

        val transactionSlot = slot<(Realm) -> Unit>()
        coEvery { databaseService.executeTransactionAsync(capture(transactionSlot)) } answers {
            transactionSlot.captured.invoke(realm)
        }

        every { realm.where(RealmNotification::class.java) } returns query
        every { query.equalTo("id", "testId") } returns query
        every { query.findFirst() } returns null
        every { realm.createObject(RealmNotification::class.java, "testId") } returns notification

        repository.insert(jsonObject)

        assertEquals("testUser", notification.userId)
        assertEquals("testMessage", notification.message)
        assertEquals("testType", notification.type)
        assertEquals("testLink", notification.link)
        assertEquals(1, notification.priority)
        assertEquals("testRev", notification.rev)
        assertTrue(notification.isRead) // "read" != "unread"
        assertEquals(123456789L, notification.createdAt.time)
        assertTrue(notification.isFromServer)
    }

    @Test
    fun `insert updates existing notification`() = runTest {
        val realm = mockk<Realm>(relaxed = true)
        val query = mockk<RealmQuery<RealmNotification>>()
        val notification = RealmNotification()
        val jsonObject = JsonObject().apply {
            addProperty("_id", "testId")
            addProperty("user", "updatedUser")
            addProperty("message", "updatedMessage")
            addProperty("type", "updatedType")
            addProperty("status", "unread")
            addProperty("time", 987654321L)
        }

        val transactionSlot = slot<(Realm) -> Unit>()
        coEvery { databaseService.executeTransactionAsync(capture(transactionSlot)) } answers {
            transactionSlot.captured.invoke(realm)
        }

        every { realm.where(RealmNotification::class.java) } returns query
        every { query.equalTo("id", "testId") } returns query
        every { query.findFirst() } returns notification

        repository.insert(jsonObject)

        assertEquals("updatedUser", notification.userId)
        assertEquals("updatedMessage", notification.message)
        assertEquals("updatedType", notification.type)
        assertFalse(notification.isRead) // "unread" == "unread" -> isRead = false
        assertEquals(987654321L, notification.createdAt.time)
        assertTrue(notification.isFromServer)

        verify(exactly = 0) { realm.createObject(any<Class<RealmNotification>>(), any<String>()) }
    }

    @Test
    fun `insert preserves read status if needsSync is true`() = runTest {
        val realm = mockk<Realm>(relaxed = true)
        val query = mockk<RealmQuery<RealmNotification>>()
        val notification = RealmNotification().apply {
            isRead = true
            needsSync = true
        }
        val jsonObject = JsonObject().apply {
            addProperty("_id", "testId")
            addProperty("status", "unread")
        }

        val transactionSlot = slot<(Realm) -> Unit>()
        coEvery { databaseService.executeTransactionAsync(capture(transactionSlot)) } answers {
            transactionSlot.captured.invoke(realm)
        }

        every { realm.where(RealmNotification::class.java) } returns query
        every { query.equalTo("id", "testId") } returns query
        every { query.findFirst() } returns notification

        repository.insert(jsonObject)

        // isRead should be preserved (true) even though status is "unread", because needsSync is true
        assertTrue(notification.isRead)
    }

    @Test
    fun `getSurveyId returns id when relatedId is not null and survey exists`() = runTest {
        val realm = mockk<Realm>(relaxed = true)
        val stepExam = RealmStepExam().apply { id = "survey123" }

        val operationSlot = slot<(Realm) -> RealmStepExam?>()
        coEvery { databaseService.withRealmAsync<RealmStepExam?>(capture(operationSlot)) } answers {
            operationSlot.captured.invoke(realm)
        }

        mockkStatic("org.ole.planet.myplanet.data.DatabaseServiceKt")
        every { realm.findCopyByField(RealmStepExam::class.java, "name", "surveyName") } returns stepExam

        val result = repository.getSurveyId("surveyName")
        assertEquals("survey123", result)
    }

    @Test
    fun `getSurveyId returns null when relatedId is null`() = runTest {
        val result = repository.getSurveyId(null)
        assertEquals(null, result)
    }

    @Test
    fun `getTaskDetails returns TaskNotificationResult when task and team exist`() = runTest {
        val realm = mockk<Realm>(relaxed = true)
        val teamTask = RealmTeamTask().apply { link = "{\"teams\":\"team123\"}" }
        val team = RealmMyTeam().apply { name = "Team Alpha"; type = "open" }

        coEvery { databaseService.withRealmAsync<Any?>(any()) } answers {
            val operation = firstArg<(Realm) -> Any?>()
            operation.invoke(realm)
        }

        mockkStatic("org.ole.planet.myplanet.data.DatabaseServiceKt")
        every { realm.findCopyByField(RealmTeamTask::class.java, "id", "task123") } returns teamTask
        every { realm.findCopyByField(RealmMyTeam::class.java, "_id", "team123") } returns team

        mockkConstructor(JSONObject::class)
        every { anyConstructed<JSONObject>().optString("teams") } returns "team123"

        val result = repository.getTaskDetails("task123")

        assertNotNull(result)
        assertEquals("team123", result?.teamId)
        assertEquals("Team Alpha", result?.teamName)
        assertEquals("open", result?.teamType)
    }

    @Test
    fun `getTaskDetails returns null when relatedId is null`() = runTest {
        val result = repository.getTaskDetails(null)
        assertEquals(null, result)
    }

    @Test
    fun `getTaskDetails returns null when task has empty teams link`() = runTest {
        val realm = mockk<Realm>(relaxed = true)
        val teamTask = RealmTeamTask().apply { link = "{}" }

        coEvery { databaseService.withRealmAsync<Any?>(any()) } answers {
            val operation = firstArg<(Realm) -> Any?>()
            operation.invoke(realm)
        }

        mockkStatic("org.ole.planet.myplanet.data.DatabaseServiceKt")
        every { realm.findCopyByField(RealmTeamTask::class.java, "id", "task123") } returns teamTask

        mockkConstructor(JSONObject::class)
        every { anyConstructed<JSONObject>().optString("teams") } returns ""

        val result = repository.getTaskDetails("task123")

        assertEquals(null, result)
    }


    @Test
    fun `getUnreadCount returns correct count when userId is not null and not admin`() = runTest {
        val realm = mockk<Realm>(relaxed = true)
        val query = mockk<RealmQuery<RealmNotification>>()

        coEvery { databaseService.withRealmAsync<Long>(any()) } answers {
            val operation = firstArg<(Realm) -> Long>()
            operation.invoke(realm)
        }

        every { realm.where(RealmNotification::class.java) } returns query
        every { query.beginGroup() } returns query
        every { query.equalTo("userId", "user123") } returns query
        every { query.endGroup() } returns query
        every { query.equalTo("isRead", false) } returns query
        every { query.count() } returns 5L

        val result = repository.getUnreadCount("user123", isAdmin = false)

        assertEquals(5, result)
        verify(exactly = 0) { query.or() }
        verify(exactly = 0) { query.equalTo("userId", "SYSTEM") }
    }

    @Test
    fun `getUnreadCount returns correct count when userId is not null and is admin`() = runTest {
        val realm = mockk<Realm>(relaxed = true)
        val query = mockk<RealmQuery<RealmNotification>>()

        coEvery { databaseService.withRealmAsync<Long>(any()) } answers {
            val operation = firstArg<(Realm) -> Long>()
            operation.invoke(realm)
        }

        every { realm.where(RealmNotification::class.java) } returns query
        every { query.beginGroup() } returns query
        every { query.equalTo("userId", "user123") } returns query
        every { query.or() } returns query
        every { query.equalTo("userId", "SYSTEM") } returns query
        every { query.endGroup() } returns query
        every { query.equalTo("isRead", false) } returns query
        every { query.count() } returns 8L

        val result = repository.getUnreadCount("user123", isAdmin = true)

        assertEquals(8, result)
        verify { query.or() }
        verify { query.equalTo("userId", "SYSTEM") }
    }

    @Test
    fun `getUnreadCount returns 0 when userId is null`() = runTest {
        val result = repository.getUnreadCount(null, isAdmin = false)
        assertEquals(0, result)
    }




    @Test
    fun `getNotifications handles filter and returns payloads`() = runTest {
        val realm = mockk<Realm>(relaxed = true)
        val query = mockk<RealmQuery<RealmNotification>>()
        val mockResults = mockk<io.realm.RealmResults<RealmNotification>>()

        val notification = RealmNotification().apply {
            id = "testId"
            userId = "user123"
            message = "Test"
            isRead = true
            createdAt = java.util.Date(1000)
            type = "type"
            priority = 1
            isFromServer = true
        }

        coEvery { databaseService.withRealmAsync<List<RealmNotification>>(any()) } answers {
            val operation = firstArg<(Realm) -> List<RealmNotification>>()
            operation.invoke(realm)
        }

        every { realm.where(RealmNotification::class.java) } returns query
        every { query.beginGroup() } returns query
        every { query.equalTo("userId", "user123") } returns query
        every { query.endGroup() } returns query
        every { query.notEqualTo("message", "INVALID") } returns query
        every { query.isNotEmpty("message") } returns query
        every { query.equalTo("isRead", true) } returns query
        every { query.sort("isRead", io.realm.Sort.ASCENDING, "createdAt", io.realm.Sort.DESCENDING) } returns query
        every { query.findAll() } returns mockResults
        every { mockResults.iterator() } returns mutableListOf(notification).iterator()
        every { realm.copyFromRealm(any<io.realm.RealmResults<RealmNotification>>()) } returns listOf(notification)

        val result = repository.getNotifications("user123", "read", isAdmin = false)

        assertEquals(1, result.size)
        assertEquals("testId", result[0].id)
        assertEquals(true, result[0].isRead)
        assertEquals(1000L, result[0].createdAt)
    }

    @Test
    fun `markAllUnreadAsRead updates relevant notifications and returns ids`() = runTest {
        val realm = mockk<Realm>(relaxed = true)
        val query = mockk<RealmQuery<RealmNotification>>()
        val mockResults = mockk<io.realm.RealmResults<RealmNotification>>()
        val notification = RealmNotification().apply { id = "testId"; isRead = false; isFromServer = true }

        val transactionSlot = slot<(Realm) -> Unit>()
        coEvery { databaseService.executeTransactionAsync(capture(transactionSlot)) } answers {
            transactionSlot.captured.invoke(realm)
        }

        every { realm.where(RealmNotification::class.java) } returns query
        every { query.equalTo("userId", "user123") } returns query
        every { query.equalTo("isRead", false) } returns query
        every { query.findAll() } returns mockResults
        every { mockResults.iterator() } returns mutableListOf(notification).iterator()

        val result = repository.markAllUnreadAsRead("user123")

        assertTrue(notification.isRead)
        assertTrue(notification.needsSync)
        assertEquals(setOf("testId"), result)
    }

    @Test
    fun `getPendingSyncNotifications executes compound query correctly`() = runTest {
        val realm = mockk<Realm>(relaxed = true)
        val query = mockk<RealmQuery<RealmNotification>>()
        val mockResults = mockk<io.realm.RealmResults<RealmNotification>>()
        val notification = RealmNotification().apply { id = "testId"; needsSync = true; rev = "1-rev" }

        coEvery { databaseService.withRealmAsync<List<RealmNotification>>(any()) } answers {
            val operation = firstArg<(Realm) -> List<RealmNotification>>()
            operation.invoke(realm)
        }

        every { realm.where(RealmNotification::class.java) } returns query
        every { query.equalTo("needsSync", true) } returns query
        every { query.isNotNull("rev") } returns query
        every { query.findAll() } returns mockResults
        every { mockResults.iterator() } returns mutableListOf(notification).iterator()
        every { realm.copyFromRealm(any<io.realm.RealmResults<RealmNotification>>()) } returns listOf(notification)

        val result = repository.getPendingSyncNotifications()

        assertEquals(1, result.size)
        assertEquals("testId", result[0].id)
    }
}
