package org.ole.planet.myplanet.data.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.TeamTask

@RunWith(AndroidJUnit4::class)
class TeamTaskDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var teamTaskDao: TeamTaskDao

    @Before
    fun initDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        teamTaskDao = database.teamTaskDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun getByIds_with1200IdsAndDuplicates_returnsAllWithoutDuplicates() = runBlocking {
        val tasks = listOf(1, 900, 901, 1200).map { i ->
            TeamTask().apply {
                id = "task_$i"
                title = "Title $i"
            }
        }
        teamTaskDao.upsertAll(tasks)

        val queryIds = (1..1200).map { i -> "task_$i" } + listOf("task_1", "task_500")
        val results = teamTaskDao.getByIds(queryIds)

        assertEquals(4, results.size)
        assertEquals(setOf("task_1", "task_900", "task_901", "task_1200"), results.map { it.id }.toSet())
    }

    @Test
    fun getByTitles_with1200TitlesAndDuplicates_returnsAllWithoutDuplicates() = runBlocking {
        val tasks = listOf(1, 900, 901, 1200).map { i ->
            TeamTask().apply {
                id = "task_$i"
                title = "Title_$i"
            }
        }
        teamTaskDao.upsertAll(tasks)

        val queryTitles = (1..1200).map { i -> "Title_$i" } + listOf("Title_1", "Title_200")
        val results = teamTaskDao.getByTitles(queryTitles)

        assertEquals(4, results.size)
        assertEquals(setOf("Title_1", "Title_900", "Title_901", "Title_1200"), results.map { it.title }.toSet())
    }

    @Test
    fun markTasksNotified_with1200IdsAndDuplicates_updatesAll() = runBlocking {
        val tasks = listOf(1, 900, 901, 1200).map { i ->
            TeamTask().apply {
                id = "task_$i"
                isNotified = false
            }
        } + TeamTask().apply {
            id = "task_control"
            isNotified = false
        }
        teamTaskDao.upsertAll(tasks)

        val queryIds = (1..1200).map { i -> "task_$i" } + listOf("task_1", "task_900")
        teamTaskDao.markTasksNotified(queryIds)

        val fetchedTasks = teamTaskDao.getByIds((1..1200).map { i -> "task_$i" })
        assertEquals(4, fetchedTasks.size)
        assertTrue(fetchedTasks.all { it.isNotified })
        assertEquals(setOf("task_1", "task_900", "task_901", "task_1200"), fetchedTasks.map { it.id }.toSet())

        val controlTask = teamTaskDao.getById("task_control")
        assertFalse(controlTask!!.isNotified)
    }

    @Test
    fun getTasksForUserBetween_returnsOnlyOpenTasks() = runBlocking {
        teamTaskDao.upsertAll(listOf(
            TeamTask().apply { id = "open"; assignee = "u1"; deadline = 500L },
            TeamTask().apply { id = "done"; assignee = "u1"; deadline = 500L; completed = true },
            TeamTask().apply { id = "archived"; assignee = "u1"; deadline = 500L; status = "archived" },
            TeamTask().apply { id = "active"; assignee = "u1"; deadline = 500L; status = "active" }
        ))

        val ids = teamTaskDao.getTasksForUserBetween("u1", 0L, 1000L).map { it.id }.toSet()

        assertEquals(setOf("open", "active"), ids)
    }

    @Test
    fun getPendingTasksForUser_excludesArchivedCompletedAndNotified() = runBlocking {
        teamTaskDao.upsertAll(listOf(
            TeamTask().apply { id = "open"; assignee = "u1"; deadline = 500L },
            TeamTask().apply { id = "done"; assignee = "u1"; deadline = 500L; completed = true },
            TeamTask().apply { id = "archived"; assignee = "u1"; deadline = 500L; status = "archived" },
            TeamTask().apply { id = "notified"; assignee = "u1"; deadline = 500L; isNotified = true },
            TeamTask().apply { id = "active"; assignee = "u1"; deadline = 500L; status = "active" }
        ))

        val ids = teamTaskDao.getPendingTasksForUser("u1", 0L, 1000L).map { it.id }.toSet()

        assertEquals(setOf("open", "active"), ids)
    }
}
