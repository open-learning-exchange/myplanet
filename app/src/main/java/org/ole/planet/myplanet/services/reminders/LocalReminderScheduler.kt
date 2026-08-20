package org.ole.planet.myplanet.services.reminders

import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.model.TeamTask

interface LocalReminderScheduler {
    suspend fun scheduleMeetupReminder(meetup: Meetup, advanceMinutes: Int = 15)
    suspend fun cancelMeetupReminder(meetupId: String)
    suspend fun scheduleTaskReminder(task: TeamTask)
    suspend fun cancelTaskReminder(taskId: String)
    suspend fun scheduleCourseReminder(course: MyCourse, triggerTime: Long)
    suspend fun cancelCourseReminder(courseId: String)
    suspend fun rescheduleAllUpcomingReminders(userId: String)
}
