package org.ole.planet.myplanet.services

import android.content.Context
import android.util.Log
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.TeamTask
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.NotificationsRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.NotificationConfig
import org.ole.planet.myplanet.utils.NotificationUtils
import org.ole.planet.myplanet.utils.TimeProvider

@OptIn(ExperimentalCoroutinesApi::class)
class TaskNotificationWorkerTest {
    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var userSessionManager: UserSessionManager
    private lateinit var teamsRepository: TeamsRepository
    private lateinit var notificationsRepository: NotificationsRepository
    private lateinit var timeProvider: TimeProvider
    private lateinit var worker: TaskNotificationWorker

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        userSessionManager = mockk(relaxed = true)
        teamsRepository = mockk(relaxed = true)
        notificationsRepository = mockk(relaxed = true)
        timeProvider = mockk(relaxed = true)

        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        mockkObject(FileUtils)
        every { FileUtils.totalAvailableMemoryRatio(any()) } returns 50L

        mockkObject(NotificationUtils)
        every { NotificationUtils.getInstance(any()) } returns mockk(relaxed = true)
        every { NotificationUtils.createTaskNotification(any(), any(), any(), any()) } returns mockk<NotificationConfig>(relaxed = true)

        worker = TaskNotificationWorker(
            context,
            workerParams,
            userSessionManager,
            teamsRepository,
            notificationsRepository,
            timeProvider
        )
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun `doWork collects valid task IDs during notification delivery and marks tasks notified`() = runTest {
        val user = UserEntity().apply { id = "user_123" }
        coEvery { userSessionManager.getUserModel() } returns user

        val task1 = TeamTask().apply { id = "task_1"; title = "Task One" }
        val task2 = TeamTask().apply { id = ""; title = "Task Two with blank ID" }
        val task3 = TeamTask().apply { id = "task_3"; title = "Task Three" }
        coEvery { teamsRepository.getPendingTasksForUser(any(), any(), any()) } returns listOf(task1, task2, task3)

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 1) {
            teamsRepository.markTasksNotified(listOf("task_1", "task_3"))
        }
    }

    @Test
    fun `doWork does not call markTasksNotified if no valid task IDs are found`() = runTest {
        val user = UserEntity().apply { id = "user_123" }
        coEvery { userSessionManager.getUserModel() } returns user

        val task1 = TeamTask().apply { id = ""; title = "Task Blank" }
        coEvery { teamsRepository.getPendingTasksForUser(any(), any(), any()) } returns listOf(task1)

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 0) {
            teamsRepository.markTasksNotified(any())
        }
    }
}
