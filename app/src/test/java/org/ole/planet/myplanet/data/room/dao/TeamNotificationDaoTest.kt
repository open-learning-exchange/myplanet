package org.ole.planet.myplanet.data.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.TeamNotification

@RunWith(AndroidJUnit4::class)
class TeamNotificationDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var notificationDao: TeamNotificationDao

    @Before
    fun initDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        notificationDao = database.teamNotificationDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun getByTypeAndParentIds_with1200IdsAndDuplicates_returnsAllMatchesWithoutDuplicates() = runBlocking {
        val notifications = (1..1200).map { i ->
            TeamNotification().apply {
                parentId = "parent_$i"
                type = "task"
                lastCount = i
            }
        }
        notifications.forEach { notificationDao.insert(it) }

        val queryParentIds = (1..1200).map { "parent_$i" } + listOf("parent_1", "parent_500")
        val results = notificationDao.getByTypeAndParentIds("task", queryParentIds)

        assertEquals(1200, results.size)
        assertEquals((1..1200).map { "parent_$i" }.toSet(), results.map { it.parentId }.toSet())
    }
}
