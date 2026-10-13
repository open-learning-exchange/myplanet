package org.ole.planet.myplanet.data.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
        val tasks = (1..1200).map { i ->
            TeamTask().apply {
                id = "task_$i"
                title = "Title $i"
            }
        }
        teamTaskDao.upsertAll(tasks)

        val queryIds = (1..1200).map { i -> "task_$i" } + listOf("task_1", "task_500")
        val results = teamTaskDao.getByIds(queryIds)

        assertEquals(1200, results.size)
        assertEquals((1..1200).map { i -> "task_$i" }.toSet(), results.map { it.id }.toSet())
    }

    @Test
    fun getByTitles_with1200TitlesAndDuplicates_returnsAllWithoutDuplicates() = runBlocking {
        val tasks = (1..1200).map { i ->
            TeamTask().apply {
                id = "task_$i"
                title = "Title_$i"
            }
        }
        teamTaskDao.upsertAll(tasks)

        val queryTitles = (1..1200).map { i -> "Title_$i" } + listOf("Title_1", "Title_200")
        val results = teamTaskDao.getByTitles(queryTitles)

        assertEquals(1200, results.size)
        assertEquals((1..1200).map { i -> "Title_$i" }.toSet(), results.map { it.title }.toSet())
    }

    @Test
    fun markTasksNotified_with1200IdsAndDuplicates_updatesAll() = runBlocking {
        val tasks = (1..1200).map { i ->
            TeamTask().apply {
                id = "task_$i"
                isNotified = false
            }
        }
        teamTaskDao.upsertAll(tasks)

        val queryIds = (1..1200).map { i -> "task_$i" } + listOf("task_1", "task_900")
        teamTaskDao.markTasksNotified(queryIds)

        val fetchedTasks = teamTaskDao.getByIds((1..1200).map { i -> "task_$i" })
        assertEquals(1200, fetchedTasks.size)
        assertTrue(fetchedTasks.all { it.isNotified })
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
}
