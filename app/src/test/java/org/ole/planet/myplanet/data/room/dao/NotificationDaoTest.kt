package org.ole.planet.myplanet.data.room.dao

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
