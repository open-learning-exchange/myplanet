package org.ole.planet.myplanet.services

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.dao.MeetupDao
import org.ole.planet.myplanet.data.room.dao.TeamDao
import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.TeamTask
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.NotificationsRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.services.reminders.LocalReminderScheduler
import org.ole.planet.myplanet.utils.NotificationUtils
import org.ole.planet.myplanet.utils.TimeProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O], application = Application::class)
class TaskNotificationWorkerTest {

    private lateinit var context: Context
    private val workerParams: WorkerParameters = mockk(relaxed = true)
    private val userSessionManager: UserSessionManager = mockk(relaxed = true)
    private val teamsRepository: TeamsRepository = mockk(relaxed = true)
    private val notificationsRepository: NotificationsRepository = mockk(relaxed = true)
    private val meetupDao: MeetupDao = mockk(relaxed = true)
    private val teamDao: TeamDao = mockk(relaxed = true)
    private val localReminderScheduler: LocalReminderScheduler = mockk(relaxed = true)
    private val timeProvider: TimeProvider = mockk()
    private val notificationManager: NotificationUtils.NotificationManager = mockk(relaxed = true)

    private val baseTime = 1700000000000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        every { timeProvider.now() } returns baseTime

        mockkObject(NotificationUtils)
        every { NotificationUtils.getInstance(any()) } returns notificationManager
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testDoWork_userLoggedIn_processesTasksAndMeetups() = runTest {
        val user = UserEntity(id = "user_123", name = "Test User")
        coEvery { userSessionManager.getUserModel() } returns user

        val task = TeamTask().apply {
            id = "task_1"
            title = "Prepare Slides"
            deadline = baseTime + 3600000L
            completed = false
        }
        coEvery { teamsRepository.getPendingTasksForUser("user_123", any(), any()) } returns listOf(task)

        val team = MyTeam(_id = "team_1").apply { teamId = "team_1" }
        coEvery { teamDao.getByUserId("user_123") } returns listOf(team)

        val meetup = Meetup().apply {
            id = "meetup_1"
            title = "Team Huddle"
            startDate = baseTime + (10 * 60 * 1000L) // in 10 minutes
            startTime = "09:00"
            teamId = "team_1"
        }
        coEvery {
            meetupDao.getUpcomingMeetupsForTeamsOrUser(listOf("team_1"), "user_123", any(), any())
        } returns listOf(meetup)

        val worker = TaskNotificationWorker(
            appContext = context,
            workerParams = workerParams,
            userSessionManager = userSessionManager,
            teamsRepository = teamsRepository,
            notificationsRepository = notificationsRepository,
            meetupDao = meetupDao,
            teamDao = teamDao,
            localReminderScheduler = localReminderScheduler,
            timeProvider = timeProvider
        )

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        // Verify task notification was triggered
        verify {
            notificationManager.showNotification(
                match { it.type == NotificationUtils.TYPE_TASK && it.id == "task_1" }
            )
        }

        // Verify meetup notification was triggered
        verify {
            notificationManager.showNotification(
                match { it.type == NotificationUtils.TYPE_MEETUP && it.id == "meetup_meetup_1" }
            )
        }

        // Verify alarm scheduler was called to reconcile
        coVerify { localReminderScheduler.rescheduleAllUpcomingReminders("user_123") }
    }

    @Test
    fun testDoWork_noLoggedInUser_returnsSuccess() = runTest {
        coEvery { userSessionManager.getUserModel() } returns null

        val worker = TaskNotificationWorker(
            appContext = context,
            workerParams = workerParams,
            userSessionManager = userSessionManager,
            teamsRepository = teamsRepository,
            notificationsRepository = notificationsRepository,
            meetupDao = meetupDao,
            teamDao = teamDao,
            localReminderScheduler = localReminderScheduler,
            timeProvider = timeProvider
        )

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { localReminderScheduler.rescheduleAllUpcomingReminders(any()) }
    }
}
