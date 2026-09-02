package org.ole.planet.myplanet.data.room.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.AppNotification
import kotlin.system.measureTimeMillis
import android.util.Log

@RunWith(AndroidJUnit4::class)
class NotificationDaoBenchmarkTest {
    private lateinit var db: AppDatabase
    private lateinit var notificationDao: NotificationDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        notificationDao = db.notificationDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun benchmarkMarkSynced() = runBlocking {
        // Insert 1000 notifications
        val notifications = (1..1000).map {
            AppNotification().apply {
                id = "id_$it"
                userId = "user"
                needsSync = true
            }
        }
        notificationDao.upsertAll(notifications)

        val syncResults = (1..1000).map {
            Pair("id_$it", "rev_$it")
        }

        // Measure time
        val time = measureTimeMillis {
            notificationDao.markSynced(syncResults)
        }

        Log.d("NotificationBenchmark", "Time to markSynced 1000 items: ${time}ms")
    }
}
