package org.ole.planet.myplanet.data.room.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.AppNotification
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class)
class NotificationDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var notificationDao: NotificationDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).allowMainThreadQueries().build()
        notificationDao = db.notificationDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testMarkSynced() = runBlocking {
        val notifications = listOf(
            AppNotification().apply { id = "1"; rev = "old1"; needsSync = true },
            AppNotification().apply { id = "2"; rev = "old2"; needsSync = true },
            AppNotification().apply { id = "3"; rev = null; needsSync = true }
        )
        notificationDao.upsertAll(notifications)

        val syncResults = listOf(
            Pair("1", "new1"),
            Pair("2", null),
            Pair("3", null)
        )

        notificationDao.markSynced(syncResults)

        val updated1 = notificationDao.getById("1")
        assertEquals(false, updated1?.needsSync)
        assertEquals("new1", updated1?.rev)

        val updated2 = notificationDao.getById("2")
        assertEquals(false, updated2?.needsSync)
        assertEquals("old2", updated2?.rev)

        val updated3 = notificationDao.getById("3")
        assertEquals(false, updated3?.needsSync)
        assertNull(updated3?.rev)
    }
}
