package org.ole.planet.myplanet.services.reminders

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.dao.CourseDao
import org.ole.planet.myplanet.data.room.dao.MeetupDao
import org.ole.planet.myplanet.data.room.dao.TeamDao
import org.ole.planet.myplanet.data.room.dao.TeamTaskDao
import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.TeamTask
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.TimeProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class LocalReminderSchedulerImplTest {

    private lateinit var context: Context
    private lateinit var spyContext: Context
    private lateinit var alarmManager: AlarmManager
    private val timeProvider: TimeProvider = mockk()
    private val meetupDao: MeetupDao = mockk(relaxed = true)
    private val teamTaskDao: TeamTaskDao = mockk(relaxed = true)
    private val teamDao: TeamDao = mockk(relaxed = true)
    private val courseDao: CourseDao = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val dispatcherProvider: DispatcherProvider = mockk {
        every { io } returns testDispatcher
        every { main } returns testDispatcher
        every { default } returns testDispatcher
    }

    private lateinit var scheduler: LocalReminderSchedulerImpl

    private val baseTime = 1700000000000L // arbitrary fixed time

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        spyContext = spyk(context)
        alarmManager = mockk(relaxed = true)
        every { spyContext.getSystemService(Context.ALARM_SERVICE) } returns alarmManager
        every { timeProvider.now() } returns baseTime

        scheduler = LocalReminderSchedulerImpl(
            context = spyContext,
            timeProvider = timeProvider,
            meetupDao = meetupDao,
            teamTaskDao = teamTaskDao,
            teamDao = teamDao,
            courseDao = courseDao,
            dispatcherProvider = dispatcherProvider
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testScheduleMeetupReminder_success() = runTest(testDispatcher) {
        val meetup = Meetup().apply {
            id = "meetup_1"
            title = "Standup"
            startDate = baseTime + (30 * 60 * 1000L) // 30 minutes in future
            startTime = "10:00 AM"
            meetupLocation = "Hall A"
            teamId = "team_1"
        }

        scheduler.scheduleMeetupReminder(meetup, advanceMinutes = 15)

        verify {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                baseTime + (15 * 60 * 1000L),
                any()
            )
        }
    }

    @Test
    fun testScheduleMeetupReminder_inPast_doesNotSchedule() = runTest(testDispatcher) {
        val pastMeetup = Meetup().apply {
            id = "meetup_past"
            title = "Old Standup"
            startDate = baseTime - 1000L
        }

        scheduler.scheduleMeetupReminder(pastMeetup)

        verify(exactly = 0) {
            alarmManager.setExactAndAllowWhileIdle(any(), any(), any())
        }
    }

    @Test
    fun testCancelMeetupReminder() = runTest(testDispatcher) {
        scheduler.cancelMeetupReminder("meetup_1")
        // Should not throw and attempts to cancel if pending intent exists
    }

    @Test
    fun testScheduleTaskReminder_success() = runTest(testDispatcher) {
        val task = TeamTask().apply {
            id = "task_1"
            title = "Complete report"
            deadline = baseTime + (2 * 60 * 60 * 1000L)
            completed = false
            teamId = "team_1"
        }

        scheduler.scheduleTaskReminder(task)

        verify {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                task.deadline,
                any()
            )
        }
    }

    @Test
    fun testScheduleTaskReminder_completedTask_doesNotSchedule() = runTest(testDispatcher) {
        val task = TeamTask().apply {
            id = "task_done"
            title = "Done task"
            deadline = baseTime + 10000L
            completed = true
        }

        scheduler.scheduleTaskReminder(task)

        verify(exactly = 0) {
            alarmManager.setExactAndAllowWhileIdle(any(), any(), any())
        }
    }

    @Test
    fun testCancelTaskReminder() = runTest(testDispatcher) {
        scheduler.cancelTaskReminder("task_1")
    }

    @Test
    fun testScheduleCourseReminder_success() = runTest(testDispatcher) {
        val course = MyCourse(
            id = "course_1"
        ).apply {
            courseTitle = "Math 101"
        }

        val triggerTime = baseTime + (60 * 60 * 1000L)
        scheduler.scheduleCourseReminder(course, triggerTime)

        verify {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                any()
            )
        }
    }

    @Test
    fun testCancelCourseReminder() = runTest(testDispatcher) {
        scheduler.cancelCourseReminder("course_1")
    }

    @Test
    fun testRescheduleAllUpcomingReminders() = runTest(testDispatcher) {
        val userId = "user_123"
        val team = MyTeam(_id = "team_1").apply { teamId = "team_1" }
        coEvery { teamDao.getByUserId(userId) } returns listOf(team)

        val upcomingMeetup = Meetup().apply {
            id = "meetup_upcoming"
            title = "Team Review"
            startDate = baseTime + (60 * 60 * 1000L)
            teamId = "team_1"
        }
        coEvery {
            meetupDao.getUpcomingMeetupsForTeamsOrUser(listOf("team_1"), userId, any(), any())
        } returns listOf(upcomingMeetup)

        val pendingTask = TeamTask().apply {
            id = "task_upcoming"
            title = "Fix Bug"
            deadline = baseTime + (2 * 60 * 60 * 1000L)
            completed = false
            teamId = "team_1"
        }
        coEvery {
            teamTaskDao.getTasksForUserBetween(userId, any(), any())
        } returns listOf(pendingTask)

        scheduler.rescheduleAllUpcomingReminders(userId)

        verify(atLeast = 1) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, any(), any())
        }
    }
}
