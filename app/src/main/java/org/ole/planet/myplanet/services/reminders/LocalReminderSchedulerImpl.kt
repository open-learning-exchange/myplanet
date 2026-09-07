package org.ole.planet.myplanet.services.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.room.dao.CourseDao
import org.ole.planet.myplanet.data.room.dao.MeetupDao
import org.ole.planet.myplanet.data.room.dao.TeamDao
import org.ole.planet.myplanet.data.room.dao.TeamTaskDao
import org.ole.planet.myplanet.data.room.dao.UserDao
import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.model.TeamTask
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.TimeProvider
import org.ole.planet.myplanet.utils.TimeUtils

@Singleton
class LocalReminderSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider,
    private val meetupDao: MeetupDao,
    private val teamTaskDao: TeamTaskDao,
    private val teamDao: TeamDao,
    private val courseDao: CourseDao,
    private val userDao: UserDao,
    private val dispatcherProvider: DispatcherProvider
) : LocalReminderScheduler {

    private val alarmManager: AlarmManager?
        get() = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    override suspend fun scheduleMeetupReminder(meetup: Meetup, advanceMinutes: Int) {
        if (meetup.startDate <= 0) return
        val now = timeProvider.now()
        if (meetup.startDate <= now) return

        val advanceMillis = advanceMinutes * 60 * 1000L
        val triggerTime = if (meetup.startDate - advanceMillis > now) {
            meetup.startDate - advanceMillis
        } else {
            now + 1000L
        }

        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_MEETUP_REMINDER
            putExtra(ReminderAlarmReceiver.EXTRA_EVENT_ID, meetup.id)
            putExtra(ReminderAlarmReceiver.EXTRA_TITLE, meetup.title ?: "Team Meetup")
            val timeInfo = TimeUtils.formatDate(meetup.startDate) +
                (if (!meetup.startTime.isNullOrBlank()) " at ${meetup.startTime}" else "")
            putExtra(ReminderAlarmReceiver.EXTRA_TIME_INFO, timeInfo)
            putExtra(ReminderAlarmReceiver.EXTRA_LOCATION, meetup.meetupLocation ?: "")
            putExtra(ReminderAlarmReceiver.EXTRA_TEAM_ID, meetup.teamId ?: "")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ("meetup_" + meetup.id).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleAlarm(triggerTime, pendingIntent)
    }

    override suspend fun cancelMeetupReminder(meetupId: String) {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_MEETUP_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ("meetup_" + meetupId).hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { cancelAlarm(it) }
    }

    override suspend fun scheduleTaskReminder(task: TeamTask) {
        if (task.deadline <= 0 || task.completed) return
        val now = timeProvider.now()
        if (task.deadline <= now) return

        val advanceList = parseAdvanceMinutes(task.reminderAdvanceMinutes)
        if (advanceList.isEmpty()) return

        val assigneeUser = task.assignee?.let { userDao.getById(it) }
        val assigneeName = assigneeUser?.getFullName()?.ifBlank { assigneeUser.name } ?: ""

        val positiveAdvances = advanceList.filter { it > 0 }.sorted()
        var scheduledFutureAdvance = false

        positiveAdvances.forEach { advanceMinutes ->
            val advanceMillis = advanceMinutes * 60 * 1000L
            val triggerTime = task.deadline - advanceMillis
            if (triggerTime > now) {
                scheduledFutureAdvance = true
                scheduleSingleTaskAlarm(task, triggerTime, advanceMinutes, assigneeName)
            }
        }

        if (!scheduledFutureAdvance && positiveAdvances.isNotEmpty()) {
            val closestAdvance = positiveAdvances.minOrNull()
            if (closestAdvance != null) {
                scheduleSingleTaskAlarm(task, now + 1000L, closestAdvance, assigneeName)
            }
        }

        if (advanceList.contains(0) && task.deadline > now) {
            scheduleSingleTaskAlarm(task, task.deadline, 0, assigneeName)
        }
    }

    private fun scheduleSingleTaskAlarm(
        task: TeamTask,
        triggerTime: Long,
        advanceMinutes: Int,
        assigneeName: String
    ) {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_TASK_REMINDER
            putExtra(ReminderAlarmReceiver.EXTRA_EVENT_ID, task.id)
            putExtra(ReminderAlarmReceiver.EXTRA_TITLE, task.title ?: "Team Task")
            putExtra(ReminderAlarmReceiver.EXTRA_DEADLINE, TimeUtils.formatDate(task.deadline))
            putExtra(ReminderAlarmReceiver.EXTRA_TEAM_ID, task.teamId ?: "")
            putExtra(ReminderAlarmReceiver.EXTRA_ADVANCE_MINUTES, advanceMinutes)
            putExtra(ReminderAlarmReceiver.EXTRA_ASSIGNEE_NAME, assigneeName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ("task_" + task.id + "_" + advanceMinutes).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleAlarm(triggerTime, pendingIntent)
    }

    private fun parseAdvanceMinutes(reminderAdvanceMinutes: String?): List<Int> {
        if (reminderAdvanceMinutes.isNullOrBlank()) return listOf(0)
        return reminderAdvanceMinutes.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .distinct()
            .ifEmpty { listOf(0) }
    }

    override suspend fun cancelTaskReminder(taskId: String) {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_TASK_REMINDER
        }
        val possibleAdvances = listOf(0, 5, 10, 15, 30, 60, 120, 1440)
        possibleAdvances.forEach { advanceMinutes ->
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ("task_" + taskId + "_" + advanceMinutes).hashCode(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let { cancelAlarm(it) }
        }
        val legacyPendingIntent = PendingIntent.getBroadcast(
            context,
            ("task_" + taskId).hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        legacyPendingIntent?.let { cancelAlarm(it) }
    }

    override suspend fun scheduleCourseReminder(course: MyCourse, triggerTime: Long) {
        if (triggerTime <= timeProvider.now()) return

        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_COURSE_REMINDER
            putExtra(ReminderAlarmReceiver.EXTRA_EVENT_ID, course.id)
            putExtra(ReminderAlarmReceiver.EXTRA_TITLE, course.courseTitle ?: "Course")
            putExtra(ReminderAlarmReceiver.EXTRA_MESSAGE, "Scheduled study session for ${course.courseTitle}")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ("course_" + course.id).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleAlarm(triggerTime, pendingIntent)
    }

    override suspend fun cancelCourseReminder(courseId: String) {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_COURSE_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ("course_" + courseId).hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { cancelAlarm(it) }
    }

    override suspend fun rescheduleAllUpcomingReminders(userId: String) = withContext(dispatcherProvider.io) {
        if (userId.isBlank()) return@withContext
        val now = timeProvider.now()
        val horizon = now + (7L * 24 * 60 * 60 * 1000)

        // 1. Reschedule Meetups
        val userTeams = teamDao.getByUserId(userId)
        val teamIds = userTeams.mapNotNull { it.teamId ?: it._id }.filter { it.isNotBlank() }
        val upcomingMeetups = if (teamIds.isNotEmpty()) {
            meetupDao.getUpcomingMeetupsForTeamsOrUser(teamIds, userId, now, horizon)
        } else {
            meetupDao.getUpcomingMeetups(now, horizon)
        }
        upcomingMeetups.forEach { meetup ->
            scheduleMeetupReminder(meetup)
        }

        // 2. Reschedule Tasks
        val pendingTasks = teamTaskDao.getTasksForUserBetween(userId, now, horizon)
            .filter { !it.completed }
        pendingTasks.forEach { task ->
            scheduleTaskReminder(task)
        }
    }

    private fun scheduleAlarm(triggerTime: Long, pendingIntent: PendingIntent) {
        val manager = alarmManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (manager.canScheduleExactAlarms()) {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                manager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: SecurityException) {
            try {
                manager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cancelAlarm(pendingIntent: PendingIntent) {
        val manager = alarmManager ?: return
        try {
            manager.cancel(pendingIntent)
            pendingIntent.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
