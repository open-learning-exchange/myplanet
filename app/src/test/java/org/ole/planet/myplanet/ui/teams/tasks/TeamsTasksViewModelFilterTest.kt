package org.ole.planet.myplanet.ui.teams.tasks

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.model.TaskStatus
import org.ole.planet.myplanet.model.TeamTask
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class TeamsTasksViewModelFilterTest {

    @Test
    fun `filterTasks correctly dispatches for all tabs`() {
        val task1Todo = TeamTask().apply { id = "1"; completed = false; status = TaskStatus.TODO.value; deadline = 100 }
        val task2InProgress = TeamTask().apply { id = "2"; completed = false; status = TaskStatus.IN_PROGRESS.value; deadline = 200 }
        val task3Completed = TeamTask().apply { id = "3"; completed = true; status = TaskStatus.COMPLETED.value; deadline = 300 }
        val task4MyTask = TeamTask().apply { id = "4"; completed = false; status = TaskStatus.TODO.value; assignee = "user1"; deadline = 400 }
        val task5OtherUser = TeamTask().apply { id = "5"; completed = false; status = TaskStatus.TODO.value; assignee = "user2"; deadline = 500 }
        val task6CompletedMyTask = TeamTask().apply { id = "6"; completed = true; status = TaskStatus.COMPLETED.value; assignee = "user1"; deadline = 600 }
        val task7NullAssignee = TeamTask().apply { id = "7"; completed = false; status = TaskStatus.TODO.value; assignee = null; deadline = 50 }

        val allTasks = listOf(task1Todo, task2InProgress, task3Completed, task4MyTask, task5OtherUser, task6CompletedMyTask, task7NullAssignee)

        val allResult = TeamsTasksViewModel.filterTasks(allTasks, TaskTab.ALL, null)
        assertEquals(listOf("5", "4", "2", "1", "7", "6", "3"), allResult.map { it.id })

        val todoResult = TeamsTasksViewModel.filterTasks(allTasks, TaskTab.TODO, null)
        assertEquals(listOf("5", "4", "1", "7"), todoResult.map { it.id })

        val inProgressResult = TeamsTasksViewModel.filterTasks(allTasks, TaskTab.IN_PROGRESS, null)
        assertEquals(listOf("2"), inProgressResult.map { it.id })

        val completedResult = TeamsTasksViewModel.filterTasks(allTasks, TaskTab.COMPLETED, null)
        assertEquals(listOf("6", "3"), completedResult.map { it.id })

        val myResult = TeamsTasksViewModel.filterTasks(allTasks, TaskTab.MINE, "user1")
        assertEquals(listOf("4"), myResult.map { it.id })

        // When userId is null, it.assignee == null matches non-completed tasks sorted by deadline descending (task2=200, task1=100, task7=50)
        val nullUserMyResult = TeamsTasksViewModel.filterTasks(allTasks, TaskTab.MINE, null)
        assertEquals(listOf("2", "1", "7"), nullUserMyResult.map { it.id })
    }

    @Test
    fun `getAssignableMembers filters members without name and fullName`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher()
        val mockTeamsRepository = mockk<TeamsRepository>()
        val mockUserRepository = mockk<UserRepository>()
        val viewModel = TeamsTasksViewModel(mockTeamsRepository, mockUserRepository, TestDispatcherProvider(testDispatcher))

        val userWithName = UserEntity().apply {
            id = "1"
            name = "alice"
            firstName = ""
            lastName = ""
        }
        val userWithFullName = UserEntity().apply {
            id = "2"
            firstName = "Bob"
            lastName = "Smith"
            name = ""
        }
        val blankUser = UserEntity().apply {
            id = "3"
            name = ""
            firstName = ""
            lastName = ""
        }
        val spaceUser = UserEntity().apply {
            id = "4"
            name = "   "
            firstName = "  "
            lastName = " "
        }

        coEvery { mockTeamsRepository.getJoinedMembers("teamA") } returns listOf(userWithName, userWithFullName, blankUser, spaceUser)

        val assignable = viewModel.getAssignableMembers("teamA")

        assertEquals(listOf("1", "2"), assignable.map { it.id })
        coVerify { mockTeamsRepository.getJoinedMembers("teamA") }
    }
}
