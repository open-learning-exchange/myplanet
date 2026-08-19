package org.ole.planet.myplanet.services.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.ole.planet.myplanet.utils.NotificationUtils
import org.ole.planet.myplanet.utils.TimeProvider

@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var timeProvider: TimeProvider

    companion object {
        const val ACTION_MEETUP_REMINDER = "org.ole.planet.myplanet.ACTION_MEETUP_REMINDER"
        const val ACTION_TASK_REMINDER = "org.ole.planet.myplanet.ACTION_TASK_REMINDER"
        const val ACTION_COURSE_REMINDER = "org.ole.planet.myplanet.ACTION_COURSE_REMINDER"

        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TIME_INFO = "extra_time_info"
        const val EXTRA_LOCATION = "extra_location"
        const val EXTRA_TEAM_ID = "extra_team_id"
        const val EXTRA_DEADLINE = "extra_deadline"
        const val EXTRA_MESSAGE = "extra_message"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val id = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""

        val notificationManager = NotificationUtils.getInstance(context)

        when (action) {
            ACTION_MEETUP_REMINDER -> {
                val timeInfo = intent.getStringExtra(EXTRA_TIME_INFO) ?: ""
                val location = intent.getStringExtra(EXTRA_LOCATION)
                val teamId = intent.getStringExtra(EXTRA_TEAM_ID)
                val config = NotificationUtils.createMeetupNotification(
                    meetupId = id,
                    meetupTitle = title,
                    timeInfo = timeInfo,
                    location = location,
                    teamId = teamId,
                    timeProvider = timeProvider
                )
                notificationManager.showNotification(config)
            }
            ACTION_TASK_REMINDER -> {
                val deadline = intent.getStringExtra(EXTRA_DEADLINE) ?: ""
                val config = NotificationUtils.createTaskNotification(
                    taskId = id,
                    taskTitle = title,
                    deadline = deadline,
                    timeProvider = timeProvider
                )
                notificationManager.showNotification(config)
            }
            ACTION_COURSE_REMINDER -> {
                val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Continue your scheduled learning session."
                val config = NotificationUtils.createCourseReminderNotification(
                    courseId = id,
                    courseTitle = title,
                    message = message,
                    timeProvider = timeProvider
                )
                notificationManager.showNotification(config)
            }
        }
    }
}
