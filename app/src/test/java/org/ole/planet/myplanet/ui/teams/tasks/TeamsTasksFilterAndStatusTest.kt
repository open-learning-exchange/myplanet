package org.ole.planet.myplanet.ui.teams.tasks

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.TeamTaskDao
import org.ole.planet.myplanet.model.TaskStatus
import org.ole.planet.myplanet.model.TeamTask
import org.ole.planet.myplanet.model.effectiveStatus
import org.ole.planet.myplanet.model.nextStatus
import org.ole.planet.myplanet.repository.TeamsRepositoryImpl

class TeamsTasksFilterAndStatusTest {

    @Test
    fun `effectiveStatus correctly resolves null, empty, and legacy tasks`() {
        val taskNullStatus = TeamTask().apply { completed = false; status = null }
        assertEquals(TaskStatus.TODO.value, taskNullStatus.effectiveStatus())

        val taskEmptyStatus = TeamTask().apply { completed = false; status = "" }
        assertEquals(TaskStatus.TODO.value, taskEmptyStatus.effectiveStatus())

        val taskExplicitTodo = TeamTask().apply { completed = false; status = "to_do" }
        assertEquals(TaskStatus.TODO.value, taskExplicitTodo.effectiveStatus())

        val taskInProgress = TeamTask().apply { completed = false; status = "in_progress" }
        assertEquals(TaskStatus.IN_PROGRESS.value, taskInProgress.effectiveStatus())

        val taskStatusCompleted = TeamTask().apply { completed = false; status = "completed" }
        assertEquals(TaskStatus.COMPLETED.value, taskStatusCompleted.effectiveStatus())

        val taskFlagCompletedEmptyStatus = TeamTask().apply { completed = true; status = "" }
        assertEquals(TaskStatus.COMPLETED.value, taskFlagCompletedEmptyStatus.effectiveStatus())

        val taskFlagCompletedTodoStatus = TeamTask().apply { completed = true; status = "to_do" }
        assertEquals(TaskStatus.COMPLETED.value, taskFlagCompletedTodoStatus.effectiveStatus())
    }

    @Test
    fun `nextStatus cycles in order todo to in_progress to completed to todo`() {
        val taskTodo = TeamTask().apply { completed = false; status = TaskStatus.TODO.value }
        assertEquals(TaskStatus.IN_PROGRESS, taskTodo.nextStatus())

        val taskEmpty = TeamTask().apply { completed = false; status = "" }
        assertEquals(TaskStatus.IN_PROGRESS, taskEmpty.nextStatus())

        val taskInProgress = TeamTask().apply { completed = false; status = TaskStatus.IN_PROGRESS.value }
        assertEquals(TaskStatus.COMPLETED, taskInProgress.nextStatus())

        val taskCompleted = TeamTask().apply { completed = true; status = TaskStatus.COMPLETED.value }
        assertEquals(TaskStatus.TODO, taskCompleted.nextStatus())
    }

    @Test
    fun `tab filtering correctly categorizes all tasks including legacy empty status`() {
        val task1LegacyTodo = TeamTask().apply { id = "1"; completed = false; status = ""; deadline = 100 }
        val task2ExplicitTodo = TeamTask().apply { id = "2"; completed = false; status = "to_do"; deadline = 200 }
        val task3InProgress = TeamTask().apply { id = "3"; completed = false; status = "in_progress"; deadline = 300 }
        val task4Completed = TeamTask().apply { id = "4"; completed = true; status = "completed"; deadline = 400 }
        val task5MyTask = TeamTask().apply { id = "5"; completed = false; status = "to_do"; assignee = "user123"; deadline = 500 }
        val task6OtherUser = TeamTask().apply { id = "6"; completed = false; status = "to_do"; assignee = "otherUser"; deadline = 600 }

        val allTasksList = listOf(task1LegacyTodo, task2ExplicitTodo, task3InProgress, task4Completed, task5MyTask, task6OtherUser)

        val todoResult = TeamsTasksFragment.filterTodoTasks(allTasksList)
        assertEquals(listOf("6", "5", "2", "1"), todoResult.map { it.id })

        val inProgressResult = TeamsTasksFragment.filterInProgressTasks(allTasksList)
        assertEquals(listOf("3"), inProgressResult.map { it.id })

        val completedResult = TeamsTasksFragment.filterCompletedTasks(allTasksList)
        assertEquals(listOf("4"), completedResult.map { it.id })

        val myTasksResult = TeamsTasksFragment.filterMyTasks(allTasksList, "user123")
        assertEquals(listOf("5"), myTasksResult.map { it.id })

        val allResult = TeamsTasksFragment.filterAllTasks(allTasksList)
        // Completed task4 must be at the bottom
        assertEquals("4", allResult.last().id)
        // Non-completed tasks sorted by deadline descending
        assertEquals(listOf("6", "5", "3", "2", "1", "4"), allResult.map { it.id })
    }

    private fun createRepository(teamTaskDao: TeamTaskDao): TeamsRepositoryImpl {
        return TeamsRepositoryImpl(
            context = mockk(relaxed = true),
            activitiesRepository = mockk(relaxed = true),
            userSessionManager = mockk(relaxed = true),
            uploadManager = mockk(relaxed = true),
            gson = mockk(relaxed = true),
            preferences = mockk(relaxed = true),
            sharedPrefManager = mockk(relaxed = true),
            serverUrlMapper = mockk(relaxed = true),
            dispatcherProvider = mockk(relaxed = true),
            userRepository = mockk(relaxed = true),
            resourcesRepositoryLazy = mockk(relaxed = true),
            timeProvider = mockk(relaxed = true),
            teamLogDao = mockk(relaxed = true),
            teamTaskDao = teamTaskDao,
            myLibraryDao = mockk(relaxed = true),
            teamDao = mockk(relaxed = true),
            courseDao = mockk(relaxed = true),
            courseStepDao = mockk(relaxed = true),
            appDatabase = mockk(relaxed = true)
        )
    }

    @Test
    fun `TeamsRepositoryImpl setTaskCompletion updates both completed flag and status`() = runTest {
        val mockTeamTaskDao = mockk<TeamTaskDao>(relaxed = true)
        val repo = createRepository(mockTeamTaskDao)

        val existingTask = TeamTask().apply { id = "t1"; completed = false; status = TaskStatus.TODO.value }
        coEvery { mockTeamTaskDao.getById("t1") } returns existingTask

        val slot = slot<TeamTask>()
        coEvery { mockTeamTaskDao.upsert(capture(slot)) } returns Unit

        repo.setTaskCompletion("t1", true)
        assertTrue(slot.captured.completed)
        assertEquals(TaskStatus.COMPLETED.value, slot.captured.status)
        assertTrue(slot.captured.isUpdated)

        repo.setTaskCompletion("t1", false)
        assertFalse(slot.captured.completed)
        assertEquals(TaskStatus.TODO.value, slot.captured.status)
        assertTrue(slot.captured.isUpdated)
    }

    @Test
    fun `TeamsRepositoryImpl setTaskStatus updates status and syncs completed boolean`() = runTest {
        val mockTeamTaskDao = mockk<TeamTaskDao>(relaxed = true)
        val repo = createRepository(mockTeamTaskDao)

        val existingTask = TeamTask().apply { id = "t1"; completed = false; status = TaskStatus.TODO.value }
        coEvery { mockTeamTaskDao.getById("t1") } returns existingTask

        val slot = slot<TeamTask>()
        coEvery { mockTeamTaskDao.upsert(capture(slot)) } returns Unit

        repo.setTaskStatus("t1", TaskStatus.IN_PROGRESS.value)
        assertEquals(TaskStatus.IN_PROGRESS.value, slot.captured.status)
        assertFalse(slot.captured.completed)

        repo.setTaskStatus("t1", TaskStatus.COMPLETED.value)
        assertEquals(TaskStatus.COMPLETED.value, slot.captured.status)
        assertTrue(slot.captured.completed)

        repo.setTaskStatus("t1", TaskStatus.TODO.value)
        assertEquals(TaskStatus.TODO.value, slot.captured.status)
        assertFalse(slot.captured.completed)
    }
}
