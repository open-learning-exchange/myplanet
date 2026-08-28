package org.ole.planet.myplanet.ui.teams.tasks

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.TeamTask
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@ExperimentalCoroutinesApi
class TeamsTasksViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    private val mockTeamsRepository = mockk<TeamsRepository>(relaxed = true)
    private val mockUserRepository = mockk<UserRepository>(relaxed = true)

    private lateinit var viewModel: TeamsTasksViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = TeamsTasksViewModel(mockTeamsRepository, mockUserRepository, dispatcherProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is null and getFormatted methods use current time fallback`() {
        assertNull(viewModel.deadline.value)

        val beforeMillis = Calendar.getInstance().timeInMillis
        val formattedDate = viewModel.getFormattedDeadlineDate()
        val formattedTime = viewModel.getFormattedDeadlineWithTime()

        val deadlineMillis = viewModel.getDeadlineMillis()
        val afterMillis = Calendar.getInstance().timeInMillis
        assertTrue(deadlineMillis in beforeMillis..afterMillis)

        assertNotNull(formattedDate)
        assertNotNull(formattedTime)
    }

    @Test
    fun `setDeadlineDate correctly updates the calendar year, month, and day`() {
        viewModel.setDeadlineDate(2025, 5, 15)

        val deadline = viewModel.deadline.value
        assertNotNull(deadline)
        assertEquals(2025, deadline?.get(Calendar.YEAR))
        assertEquals(5, deadline?.get(Calendar.MONTH))
        assertEquals(15, deadline?.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `setDeadlineTime updates existing calendar if present`() {
        viewModel.setDeadlineDate(2025, 5, 15)
        viewModel.setDeadlineTime(14, 30)

        val deadline = viewModel.deadline.value
        assertNotNull(deadline)
        assertEquals(2025, deadline?.get(Calendar.YEAR))
        assertEquals(5, deadline?.get(Calendar.MONTH))
        assertEquals(15, deadline?.get(Calendar.DAY_OF_MONTH))
        assertEquals(14, deadline?.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, deadline?.get(Calendar.MINUTE))
    }

    @Test
    fun `setDeadlineTime falls back to current date if calendar is not present`() {
        viewModel.setDeadlineTime(14, 30)

        val deadline = viewModel.deadline.value
        assertNotNull(deadline)
        assertEquals(14, deadline?.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, deadline?.get(Calendar.MINUTE))
    }

    @Test
    fun `clearDeadline resets the state to null`() {
        viewModel.setDeadlineDate(2025, 5, 15)
        assertNotNull(viewModel.deadline.value)

        viewModel.clearDeadline()
        assertNull(viewModel.deadline.value)
    }

    @Test
    fun `setDeadline updates from long timestamp`() {
        val calendar = Calendar.getInstance()
        calendar.set(2026, 1, 1, 12, 0, 0)
        val millis = calendar.timeInMillis

        viewModel.setDeadline(millis)

        val deadline = viewModel.deadline.value
        assertNotNull(deadline)
        assertEquals(millis, deadline?.timeInMillis)
    }

    @Test
    fun `createOrUpdateTask creates task when teamTask is null`() = runTest(testDispatcher) {
        val title = "New Task"
        val desc = "Task Description"
        val teamId = "team123"
        val assigneeId = "user1"
        viewModel.setDeadline(1000L)

        val events = mutableListOf<TaskActionEvent>()
        val job = backgroundScope.launch(testDispatcher) { viewModel.taskActionEvents.collect { events.add(it) } }

        viewModel.createOrUpdateTask(title, desc, null, teamId, assigneeId)
        yield()

        coVerify { mockTeamsRepository.createTask(title, desc, viewModel.getDeadlineMillis(), teamId, assigneeId) }
        val event = events.lastOrNull()
        assertTrue(event is TaskActionEvent.TaskCreatedOrUpdated && event.isCreated && event.assigneeId == assigneeId)
        job.cancel()
    }

    @Test
    fun `createOrUpdateTask updates task when teamTask is not null`() = runTest(testDispatcher) {
        val title = "Updated Task"
        val desc = "Updated Description"
        val teamId = "team123"
        val assigneeId = "user2"
        val mockTeamTask = TeamTask()
        mockTeamTask.id = "task1"
        mockTeamTask._id = "task1"
        viewModel.setDeadline(2000L)

        val events = mutableListOf<TaskActionEvent>()
        val job = backgroundScope.launch(testDispatcher) { viewModel.taskActionEvents.collect { events.add(it) } }

        viewModel.createOrUpdateTask(title, desc, mockTeamTask, teamId, assigneeId)
        yield()

        coVerify { mockTeamsRepository.updateTask("task1", title, desc, viewModel.getDeadlineMillis(), assigneeId) }
        val event = events.lastOrNull()
        assertTrue(event is TaskActionEvent.TaskCreatedOrUpdated && !event.isCreated && event.assigneeId == assigneeId)
        job.cancel()
    }

    @Test
    fun `deleteTask calls repository and emits event`() = runTest(testDispatcher) {
        val events = mutableListOf<TaskActionEvent>()
        val job = backgroundScope.launch(testDispatcher) { viewModel.taskActionEvents.collect { events.add(it) } }

        viewModel.deleteTask("task1")
        yield()

        coVerify { mockTeamsRepository.deleteTask("task1") }
        val event = events.lastOrNull()
        assertTrue(event is TaskActionEvent.TaskDeleted)
        job.cancel()
    }

    @Test
    fun `setTaskCompletion calls repository`() = runTest(testDispatcher) {
        viewModel.setTaskCompletion("task1", true)
        yield()

        coVerify { mockTeamsRepository.setTaskCompletion("task1", true) }
    }

    @Test
    fun `setTaskStatus calls repository`() = runTest(testDispatcher) {
        viewModel.setTaskStatus("task1", "in_progress")
        yield()

        coVerify { mockTeamsRepository.setTaskStatus("task1", "in_progress") }
    }

    @Test
    fun `getJoinedMembers returns list from repository`() = runTest(testDispatcher) {
        val mockList = listOf(mockk<UserEntity>())
        coEvery { mockTeamsRepository.getJoinedMembers("team1") } returns mockList

        val result = viewModel.getJoinedMembers("team1")

        assertEquals(mockList, result)
        coVerify { mockTeamsRepository.getJoinedMembers("team1") }
    }

    @Test
    fun `assignTask calls repository and emits event`() = runTest(testDispatcher) {
        val mockUser = UserEntity()
        mockUser.id = "user1"
        mockUser.name = "John Doe"

        val events = mutableListOf<TaskActionEvent>()
        val job = backgroundScope.launch(testDispatcher) { viewModel.taskActionEvents.collect { events.add(it) } }

        viewModel.assignTask("task1", mockUser)
        yield()

        coVerify { mockTeamsRepository.assignTask("task1", "user1") }
        val event = events.lastOrNull()
        assertTrue(event is TaskActionEvent.TaskAssigned && event.userName == "John Doe")
        job.cancel()
    }

    @Test
    fun `getAssignee returns user from userRepository`() = runTest(testDispatcher) {
        val mockUser = mockk<UserEntity>()
        coEvery { mockUserRepository.getUserById("user1") } returns mockUser

        val result = viewModel.getAssignee("user1")

        assertEquals(mockUser, result)
        coVerify { mockUserRepository.getUserById("user1") }
    }

    @Test
    fun `fetchAssigneeNames uses batch api`() = runTest(testDispatcher) {
        val mockUser1 = UserEntity().apply {
            id = "1"
            name = "Name1"
        }
        val mockUser2 = UserEntity().apply {
            id = "2"
            name = "Name2"
        }

        coEvery { mockUserRepository.getUsersByIds(listOf("1", "2")) } returns listOf(mockUser1, mockUser2)

        val result = viewModel.fetchAssigneeNames(listOf("1", "2"))

        assertEquals(mapOf("1" to "Name1", "2" to "Name2"), result)
        coVerify { mockUserRepository.getUsersByIds(listOf("1", "2")) }
    }
}
