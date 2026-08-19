package org.ole.planet.myplanet.services

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import org.ole.planet.myplanet.data.room.dao.MeetupDao
import org.ole.planet.myplanet.data.room.dao.TeamDao
import org.ole.planet.myplanet.repository.NotificationsRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.services.reminders.LocalReminderScheduler
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.NotificationUtils
import org.ole.planet.myplanet.utils.TimeProvider
import org.ole.planet.myplanet.utils.TimeUtils.formatDate

@HiltWorker
class TaskNotificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val userSessionManager: UserSessionManager,
    private val teamsRepository: TeamsRepository,
    private val notificationsRepository: NotificationsRepository,
    private val meetupDao: MeetupDao,
    private val teamDao: TeamDao,
    private val localReminderScheduler: LocalReminderScheduler,
    private val timeProvider: TimeProvider
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val current = timeProvider.now()
        val tomorrow = Calendar.getInstance().apply {
            timeInMillis = current
            add(Calendar.DAY_OF_YEAR, 1)
        }

        val user = userSessionManager.getUserModel()
        val userId = user?.id
        if (!userId.isNullOrBlank()) {
            runCatching {
                val availablePercent = FileUtils.totalAvailableMemoryRatio(applicationContext).toInt()
                notificationsRepository.updateStorageNotification(userId, availablePercent)
            }

            val notificationManager = NotificationUtils.getInstance(applicationContext)

            // 1. Process Task Notifications
            val tasks = runCatching {
                teamsRepository.getPendingTasksForUser(userId, current, tomorrow.timeInMillis)
            }.getOrElse { emptyList() }

            if (tasks.isNotEmpty()) {
                tasks.forEach { task ->
                    val config = NotificationUtils.createTaskNotification(
                        task.id,
                        task.title.orEmpty(),
                        formatDate(task.deadline),
                        timeProvider
                    )
                    notificationManager.showNotification(config)
                }

                val taskIds = tasks.mapNotNull { it.id }.filter { it.isNotBlank() }
                if (taskIds.isNotEmpty()) {
                    runCatching { teamsRepository.markTasksNotified(taskIds) }
                }
            }

            // 2. Process Upcoming Meetup Reminders (next 30 minutes)
            runCatching {
                val userTeams = teamDao.getByUserId(userId)
                val teamIds = userTeams.mapNotNull { it.teamId ?: it._id }.filter { it.isNotBlank() }
                val next30Minutes = current + (30 * 60 * 1000L)
                val upcomingMeetups = if (teamIds.isNotEmpty()) {
                    meetupDao.getUpcomingMeetupsForTeamsOrUser(teamIds, userId, current, next30Minutes)
                } else {
                    meetupDao.getUpcomingMeetups(current, next30Minutes)
                }

                upcomingMeetups.forEach { meetup ->
                    val timeInfo = formatDate(meetup.startDate) +
                        (if (!meetup.startTime.isNullOrBlank()) " at ${meetup.startTime}" else "")
                    val config = NotificationUtils.createMeetupNotification(
                        meetupId = meetup.id,
                        meetupTitle = meetup.title ?: "Team Meetup",
                        timeInfo = timeInfo,
                        location = meetup.meetupLocation,
                        teamId = meetup.teamId,
                        timeProvider = timeProvider
                    )
                    notificationManager.showNotification(config)
                }
            }

            // 3. Reconcile Exact Alarms for upcoming week
            runCatching {
                localReminderScheduler.rescheduleAllUpcomingReminders(userId)
            }
        }
        return Result.success()
    }
}

