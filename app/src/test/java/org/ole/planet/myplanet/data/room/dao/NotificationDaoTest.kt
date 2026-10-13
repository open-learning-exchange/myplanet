package org.ole.planet.myplanet.data.room.dao

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.AppNotification
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class NotificationDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var notificationDao: NotificationDao

    @Before
    fun initDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        notificationDao = database.notificationDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun createNotification(
        id: String,
        rev: String? = null,
        needsSync: Boolean = true
    ): AppNotification {
        return AppNotification().apply {
            this.id = id
            this.rev = rev
            this.needsSync = needsSync
        }
    }

    @Test
    fun markSynced_updatesNonNullRevAndClearsNeedsSync() = runBlocking {
        val notif = createNotification("notif1", rev = "old_rev", needsSync = true)
        notificationDao.upsert(notif)

        notificationDao.markSynced(listOf("notif1" to "new_rev"))

        val result = notificationDao.getById("notif1")
        assertEquals("new_rev", result?.rev)
        assertFalse(result?.needsSync ?: true)
    }

    @Test
    fun markSynced_nullRevLeavesExistingRevUnchangedAndClearsNeedsSync() = runBlocking {
        val notif = createNotification("notif2", rev = "existing_rev", needsSync = true)
        notificationDao.upsert(notif)

        notificationDao.markSynced(listOf("notif2" to null))

        val result = notificationDao.getById("notif2")
        assertEquals("existing_rev", result?.rev)
        assertFalse(result?.needsSync ?: true)
    }

    @Test
    fun markSynced_nullRevWithNullRevLeavesRevNullAndClearsNeedsSync() = runBlocking {
        val notif = createNotification("notif3", rev = null, needsSync = true)
        notificationDao.upsert(notif)

        notificationDao.markSynced(listOf("notif3" to null))

        val result = notificationDao.getById("notif3")
        assertNull(result?.rev)
        assertFalse(result?.needsSync ?: true)
    }

    @Test
    fun markSynced_handlesMixedNonNullAndNullRevsInBatch() = runBlocking {
        val n1 = createNotification("n1", rev = "rev1_old", needsSync = true)
        val n2 = createNotification("n2", rev = "rev2_old", needsSync = true)
        val n3 = createNotification("n3", rev = null, needsSync = true)
        notificationDao.upsertAll(listOf(n1, n2, n3))

        notificationDao.markSynced(
            listOf(
                "n1" to "rev1_new",
                "n2" to null,
                "n3" to null
            )
        )

        val res1 = notificationDao.getById("n1")
        val res2 = notificationDao.getById("n2")
        val res3 = notificationDao.getById("n3")

        assertEquals("rev1_new", res1?.rev)
        assertFalse(res1?.needsSync ?: true)

        assertEquals("rev2_old", res2?.rev)
        assertFalse(res2?.needsSync ?: true)

        assertNull(res3?.rev)
        assertFalse(res3?.needsSync ?: true)
    }

    @Test
    fun markSynced_handlesChunkingForLargeLists() = runBlocking {
        val nonNullItems = (1..300).map { i ->
            createNotification("nonNull_$i", rev = "old_$i", needsSync = true)
        }
        val nullItems = (1..950).map { i ->
            createNotification("null_$i", rev = "old_null_$i", needsSync = true)
        }
        notificationDao.upsertAll(nonNullItems + nullItems)

        val syncResults = nonNullItems.map { it.id to "new_${it.id}" } + nullItems.map { it.id to null }
        notificationDao.markSynced(syncResults)

        val checkNonNull = notificationDao.getById("nonNull_300")
        assertEquals("new_nonNull_300", checkNonNull?.rev)
        assertFalse(checkNonNull?.needsSync ?: true)

        val checkNull = notificationDao.getById("null_950")
        assertEquals("old_null_950", checkNull?.rev)
        assertFalse(checkNull?.needsSync ?: true)
    }

    @Test
    fun markSynced_emptyListDoesNothing() = runBlocking {
        val notif = createNotification("notif_empty", rev = "rev", needsSync = true)
        notificationDao.upsert(notif)

        notificationDao.markSynced(emptyList())

        val result = notificationDao.getById("notif_empty")
        assertEquals("rev", result?.rev)
        assertTrue(result?.needsSync ?: false)
    }

    @Test
    fun chunkedListOperations_deduplicateRepeatedIdsAcrossChunkBoundary() = runBlocking {
        val notifications = (1..1000).map { i ->
            createNotification("notif_$i", rev = "rev_$i", needsSync = true).apply {
                isRead = false
            }
        }
        notificationDao.upsertAll(notifications)

        val idsWithDuplicate = notifications.map { it.id } + "notif_1"
        assertEquals(1001, idsWithDuplicate.size)

        // Test getByIds
        val fetchedNotifications = notificationDao.getByIds(idsWithDuplicate)
        assertEquals(1000, fetchedNotifications.size)

        // Test getIdsByIds
        val fetchedIds = notificationDao.getIdsByIds(idsWithDuplicate)
        assertEquals(1000, fetchedIds.size)

        // Test markAsRead(ids, date) returns distinct count
        val markDate = java.util.Date()
        val markResult = notificationDao.markAsRead(idsWithDuplicate, markDate)
        assertEquals(1000, markResult)

        // Test deleteByIds returns distinct count
        val deleteResult = notificationDao.deleteByIds(idsWithDuplicate)
        assertEquals(1000, deleteResult)

        val remainingNotifications = notificationDao.getByIds(idsWithDuplicate)
        assertTrue(remainingNotifications.isEmpty())
    }

    @Test
    fun chunkedListOperations_handleMoreThan900Items() = runBlocking {
        val notifications = (1..1200).map { i ->
            createNotification("notif_$i", rev = "rev_$i", needsSync = true).apply {
                isRead = false
            }
        }
        notificationDao.upsertAll(notifications)

        val ids = notifications.map { it.id }

        // Test getByIds
        val fetchedNotifications = notificationDao.getByIds(ids)
        assertEquals(1200, fetchedNotifications.size)

        // Test getIdsByIds
        val fetchedIds = notificationDao.getIdsByIds(ids)
        assertEquals(1200, fetchedIds.size)

        // Test markAsRead(ids, date)
        val markDate = java.util.Date()
        val markResult = notificationDao.markAsRead(ids, markDate)
        assertEquals(1200, markResult)

        val updatedNotifications = notificationDao.getByIds(ids)
        assertTrue(updatedNotifications.all { it.isRead })

        // Test deleteByIds
        val deleteResult = notificationDao.deleteByIds(ids)
        assertEquals(1200, deleteResult)

        val remainingNotifications = notificationDao.getByIds(ids)
        assertTrue(remainingNotifications.isEmpty())
    }

    @Test
    fun chunkedListOperations_handleEmptyInputs() = runBlocking {
        assertTrue(notificationDao.getByIds(emptyList()).isEmpty())
        assertTrue(notificationDao.getIdsByIds(emptyList()).isEmpty())
        assertEquals(0, notificationDao.markAsRead(emptyList(), java.util.Date()))
        assertEquals(0, notificationDao.deleteByIds(emptyList()))
    }

    @Test
    fun upsertAllPreservingPendingRead_rowWithNeedsSyncPreservesIsReadAndNeedsSync() = runBlocking {
        val existing = createNotification("n1", needsSync = true).apply { isRead = true }
        notificationDao.upsert(existing)

        val serverCopy = createNotification("n1", needsSync = false).apply { isRead = false }
        notificationDao.upsertAllPreservingPendingRead(listOf(serverCopy))

        val result = notificationDao.getById("n1")
        assertNotNull(result)
        assertTrue(result!!.isRead)
        assertTrue(result.needsSync)
    }

    @Test
    fun upsertAllPreservingPendingRead_rowWithoutNeedsSyncTakesServerIsRead() = runBlocking {
        val existing = createNotification("n2", needsSync = false).apply { isRead = true }
        notificationDao.upsert(existing)

        val serverCopy = createNotification("n2", needsSync = false).apply { isRead = false }
        notificationDao.upsertAllPreservingPendingRead(listOf(serverCopy))

        val result = notificationDao.getById("n2")
        assertNotNull(result)
        assertFalse(result!!.isRead)
        assertFalse(result.needsSync)
    }

    @Test
    fun markExistingAsRead_returnsOnlyExistingIdsAndChangesOnlyThoseRows() = runBlocking {
        val n1 = createNotification("n1").apply { isRead = false }
        val n2 = createNotification("n2").apply { isRead = false }
        notificationDao.upsertAll(listOf(n1, n2))

        val result = notificationDao.markExistingAsRead(listOf("n1", "n3"), java.util.Date())

        assertEquals(listOf("n1"), result)
        assertTrue(notificationDao.getById("n1")!!.isRead)
        assertFalse(notificationDao.getById("n2")!!.isRead)
        assertNull(notificationDao.getById("n3"))
    }

    @Test
    fun deleteExisting_returnsOnlyExistingIdsAndDeletesOnlyThoseRows() = runBlocking {
        val n1 = createNotification("n1")
        val n2 = createNotification("n2")
        notificationDao.upsertAll(listOf(n1, n2))

        val result = notificationDao.deleteExisting(listOf("n1", "n3"))

        assertEquals(listOf("n1"), result)
        assertNull(notificationDao.getById("n1"))
        assertNotNull(notificationDao.getById("n2"))
    }

    @Test
    fun markAllUnreadAsReadReturningIds_returnsExactlyFlippedIdsScopedToUserId() = runBlocking {
        val userA1 = createNotification("userA_1").apply { userId = "userA"; isRead = false }
        val userA2 = createNotification("userA_2").apply { userId = "userA"; isRead = true }
        val userB1 = createNotification("userB_1").apply { userId = "userB"; isRead = false }
        notificationDao.upsertAll(listOf(userA1, userA2, userB1))

        val result = notificationDao.markAllUnreadAsReadReturningIds("userA", java.util.Date())

        assertEquals(listOf("userA_1"), result)
        assertTrue(notificationDao.getById("userA_1")!!.isRead)
        assertTrue(notificationDao.getById("userA_2")!!.isRead)
        assertFalse(notificationDao.getById("userB_1")!!.isRead)
    }

    @Test
    fun markAllUnreadAsReadReturningIds_includesSystemNotificationsOnlyForAdmins() = runBlocking {
        val own = createNotification("own").apply { userId = "admin1"; isRead = false }
        val system = createNotification("system").apply { userId = "SYSTEM"; isRead = false }
        notificationDao.upsertAll(listOf(own, system))

        val asRegularUser = notificationDao.markAllUnreadAsReadReturningIds("admin1", java.util.Date())
        assertEquals(listOf("own"), asRegularUser)
        assertFalse(notificationDao.getById("system")!!.isRead)

        val asAdmin = notificationDao.markAllUnreadAsReadReturningIds("admin1", java.util.Date(), isAdmin = true)
        assertEquals(listOf("system"), asAdmin)
        assertTrue(notificationDao.getById("system")!!.isRead)
        assertEquals(0, notificationDao.getUnreadCount("admin1", true))
    }

    @Test
    fun getNotifications_emptyFilter_returnsReadAndUnread_unreadFirst_thenCreatedAtDesc() = runBlocking {
        val n1 = AppNotification().apply { id = "n1"; userId = "u1"; message = "m1"; isRead = true; createdAt = java.util.Date(1000L) }
        val n2 = AppNotification().apply { id = "n2"; userId = "u1"; message = "m2"; isRead = true; createdAt = java.util.Date(2000L) }
        val n3 = AppNotification().apply { id = "n3"; userId = "u1"; message = "m3"; isRead = false; createdAt = java.util.Date(1000L) }
        val n4 = AppNotification().apply { id = "n4"; userId = "u1"; message = "m4"; isRead = false; createdAt = java.util.Date(2000L) }
        notificationDao.upsertAll(listOf(n1, n2, n3, n4))

        val result = notificationDao.getNotifications("u1", "", false)

        assertEquals(4, result.size)
        assertEquals(listOf("n4", "n3", "n2", "n1"), result.map { it.id })
    }

    @Test
    fun getNotifications_readAndUnreadFilters_returnOnlyMatchingRows() = runBlocking {
        val readNotif = AppNotification().apply { id = "n_read"; userId = "u1"; message = "m1"; isRead = true; createdAt = java.util.Date(1000L) }
        val unreadNotif = AppNotification().apply { id = "n_unread"; userId = "u1"; message = "m2"; isRead = false; createdAt = java.util.Date(2000L) }
        notificationDao.upsertAll(listOf(readNotif, unreadNotif))

        val readResults = notificationDao.getNotifications("u1", "read", false)
        assertEquals(1, readResults.size)
        assertEquals("n_read", readResults[0].id)

        val unreadResults = notificationDao.getNotifications("u1", "unread", false)
        assertEquals(1, unreadResults.size)
        assertEquals("n_unread", unreadResults[0].id)
    }

    @Test
    fun getNotifications_excludesInvalidAndEmptyMessages() = runBlocking {
        val valid = AppNotification().apply { id = "n_valid"; userId = "u1"; message = "Valid message" }
        val invalid = AppNotification().apply { id = "n_invalid"; userId = "u1"; message = "INVALID" }
        val emptyMsg = AppNotification().apply { id = "n_empty"; userId = "u1"; message = "" }
        notificationDao.upsertAll(listOf(valid, invalid, emptyMsg))

        val result = notificationDao.getNotifications("u1", "", false)

        assertEquals(1, result.size)
        assertEquals("n_valid", result[0].id)
    }

    @Test
    fun getNotifications_systemRowReturnedOnlyWhenIsAdminIsTrue() = runBlocking {
        val userNotif = AppNotification().apply { id = "n_user"; userId = "u1"; message = "User Message" }
        val systemNotif = AppNotification().apply { id = "n_sys"; userId = "SYSTEM"; message = "System Message" }
        notificationDao.upsertAll(listOf(userNotif, systemNotif))

        val nonAdminResult = notificationDao.getNotifications("u1", "", isAdmin = false)
        assertEquals(listOf("n_user"), nonAdminResult.map { it.id })

        val adminResult = notificationDao.getNotifications("u1", "", isAdmin = true)
        assertEquals(setOf("n_user", "n_sys"), adminResult.map { it.id }.toSet())
    }

    @Test
    fun getNotifications_createdAtMillisRoundTripExactly() = runBlocking {
        val expectedMillis = 1700000000123L
        val notif = AppNotification().apply {
            id = "n_time"
            userId = "u1"
            message = "Time Test"
            createdAt = java.util.Date(expectedMillis)
        }
        notificationDao.upsert(notif)

        val result = notificationDao.getNotifications("u1", "", false)

        assertEquals(1, result.size)
        assertEquals(expectedMillis, result[0].createdAt)
    }
}
