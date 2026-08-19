package org.ole.planet.myplanet.services.reminders

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.utils.NotificationUtils
import org.ole.planet.myplanet.utils.TimeProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O], application = Application::class)
class ReminderAlarmReceiverTest {

    private lateinit var receiver: ReminderAlarmReceiver
    private lateinit var context: Context
    private val timeProvider: TimeProvider = mockk()
    private val notificationManager: NotificationUtils.NotificationManager = mockk(relaxed = true)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        every { timeProvider.now() } returns 1700000000000L

        mockkObject(NotificationUtils)
        every { NotificationUtils.getInstance(any()) } returns notificationManager

        receiver = spyk(ReminderAlarmReceiver().apply {
            timeProvider = this@ReminderAlarmReceiverTest.timeProvider
        })
        try {
            val injectedField = Hilt_ReminderAlarmReceiver::class.java.getDeclaredField("injected")
            injectedField.isAccessible = true
            injectedField.set(receiver, true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testOnReceive_meetupReminder_showsNotification() {
        val intent = Intent(ReminderAlarmReceiver.ACTION_MEETUP_REMINDER).apply {
            putExtra(ReminderAlarmReceiver.EXTRA_EVENT_ID, "meetup_123")
            putExtra(ReminderAlarmReceiver.EXTRA_TITLE, "Sprint Planning")
            putExtra(ReminderAlarmReceiver.EXTRA_TIME_INFO, "Tomorrow at 10:00 AM")
            putExtra(ReminderAlarmReceiver.EXTRA_LOCATION, "Conference Room")
            putExtra(ReminderAlarmReceiver.EXTRA_TEAM_ID, "team_456")
        }

        receiver.onReceive(context, intent)

        verify {
            notificationManager.showNotification(
                match { config ->
                    config.type == NotificationUtils.TYPE_MEETUP &&
                        config.id == "meetup_meetup_123" &&
                        config.message.contains("Sprint Planning")
                }
            )
        }
    }

    @Test
    fun testOnReceive_taskReminder_showsNotification() {
        val intent = Intent(ReminderAlarmReceiver.ACTION_TASK_REMINDER).apply {
            putExtra(ReminderAlarmReceiver.EXTRA_EVENT_ID, "task_789")
            putExtra(ReminderAlarmReceiver.EXTRA_TITLE, "Submit Homework")
            putExtra(ReminderAlarmReceiver.EXTRA_DEADLINE, "2026-08-20")
            putExtra(ReminderAlarmReceiver.EXTRA_TEAM_ID, "team_456")
        }

        receiver.onReceive(context, intent)

        verify {
            notificationManager.showNotification(
                match { config ->
                    config.type == NotificationUtils.TYPE_TASK &&
                        config.id == "task_789" &&
                        config.message.contains("Submit Homework")
                }
            )
        }
    }

    @Test
    fun testOnReceive_courseReminder_showsNotification() {
        val intent = Intent(ReminderAlarmReceiver.ACTION_COURSE_REMINDER).apply {
            putExtra(ReminderAlarmReceiver.EXTRA_EVENT_ID, "course_101")
            putExtra(ReminderAlarmReceiver.EXTRA_TITLE, "Science Basics")
            putExtra(ReminderAlarmReceiver.EXTRA_MESSAGE, "Time for your chapter review")
        }

        receiver.onReceive(context, intent)

        verify {
            notificationManager.showNotification(
                match { config ->
                    config.type == NotificationUtils.TYPE_COURSE &&
                        config.id == "course_course_101" &&
                        config.message.contains("Science Basics")
                }
            )
        }
    }

    @Test
    fun testOnReceive_missingId_doesNotShowNotification() {
        val intent = Intent(ReminderAlarmReceiver.ACTION_MEETUP_REMINDER)
        receiver.onReceive(context, intent)

        verify(exactly = 0) {
            notificationManager.showNotification(any())
        }
    }
}
