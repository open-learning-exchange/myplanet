package org.ole.planet.myplanet.utils

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O], application = Application::class)
class NotificationUtilsTest {

    private lateinit var timeProvider: TimeProvider

    @Before
    fun setUp() {
        timeProvider = mockk()
        every { timeProvider.now() } returns System.currentTimeMillis()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testCreateTaskNotification_Urgent() {
        val taskId = "task_1"
        val taskTitle = "Urgent Task"
        val deadline = "2023-01-01"

        mockkObject(TimeUtils)
        every { TimeUtils.parseDate(deadline) } returns System.currentTimeMillis() + (1000 * 60 * 60 * 24 * 1) // 1 day in future

        val config = NotificationUtils.createTaskNotification(taskId, taskTitle, deadline, timeProvider)

        assertEquals(NotificationCompat.PRIORITY_HIGH, config.priority)
        assertEquals(NotificationUtils.TYPE_TASK, config.type)
        assertEquals(taskId, config.id)
        assertTrue(config.message.contains(taskTitle))
        assertTrue(config.message.contains(deadline))
    }

    @Test
    fun testCreateTaskNotification_NotUrgent() {
        val taskId = "task_2"
        val taskTitle = "Not Urgent Task"
        val deadline = "2023-01-01"

        mockkObject(TimeUtils)
        every { TimeUtils.parseDate(deadline) } returns System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 5) // 5 days in future

        val config = NotificationUtils.createTaskNotification(taskId, taskTitle, deadline, timeProvider)

        assertEquals(NotificationCompat.PRIORITY_DEFAULT, config.priority)
    }

    @Test
    fun testNotificationManagerInit() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()

        val spyContext = spyk(context)

        every { spyContext.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.getStringSet(any(), any()) } returns emptySet()
        every { prefs.edit() } returns editor
        every { editor.putStringSet(any(), any()) } returns editor
        every { editor.apply() } returns Unit

        val notificationManagerMock = mockk<NotificationManager>(relaxed = true)
        every { spyContext.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManagerMock

        val manager = NotificationUtils.NotificationManager(spyContext, timeProvider)

        assertNotNull(manager)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            verify(atLeast = 1) { notificationManagerMock.createNotificationChannel(any()) }
        }
    }

    @Test
    fun testCreate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val spyContext = spyk(context)
        val manager = mockk<NotificationManager>(relaxed = true)

        every { spyContext.getSystemService(Context.NOTIFICATION_SERVICE) } returns manager

        NotificationUtils.create(spyContext, 123, "Test Title", "Test Text")

        verify { manager.notify(NotificationUtils.RECORDING_NOTIFICATION_ID, any()) }
    }

    @Test
    fun cancelRecordingNotification_leavesOtherNotificationsPosted() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        NotificationUtils.create(context, android.R.drawable.ic_btn_speak_now, "Recording Audio", "recording")
        manager.notify(42, NotificationCompat.Builder(context, "11").setSmallIcon(android.R.drawable.ic_dialog_info).build())

        NotificationUtils.cancel(context, NotificationUtils.RECORDING_NOTIFICATION_ID)

        assertEquals(listOf(42), manager.activeNotifications.map { it.id })
    }
}
