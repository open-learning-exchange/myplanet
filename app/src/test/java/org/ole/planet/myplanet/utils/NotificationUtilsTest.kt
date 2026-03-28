package org.ole.planet.myplanet.utils

import android.content.Context
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
@Config(sdk = [Build.VERSION_CODES.O], application = android.app.Application::class)
class NotificationUtilsTest {

    @Before
    fun setUp() {
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testCreateSurveyNotification() {
        val surveyId = "test_survey_123"
        val surveyTitle = "Test Survey Title"

        val config = NotificationUtils.createSurveyNotification(surveyId, surveyTitle)

        assertEquals(surveyId, config.id)
        assertEquals(NotificationUtils.TYPE_SURVEY, config.type)
        assertEquals("📋 New Survey Available", config.title)
        assertEquals(surveyTitle, config.message)
        assertEquals(NotificationCompat.PRIORITY_HIGH, config.priority)
        assertEquals(NotificationCompat.CATEGORY_REMINDER, config.category)
        assertTrue(config.actionable)
        assertEquals(surveyId, config.extras["surveyId"])
        assertEquals(surveyId, config.relatedId)
    }

    @Test
    fun testCreateTaskNotification_Urgent() {
        val taskId = "task_1"
        val taskTitle = "Urgent Task"
        val deadline = "2023-01-01"

        mockkObject(TimeUtils)
        every { TimeUtils.parseDate(deadline) } returns System.currentTimeMillis() + (1000 * 60 * 60 * 24 * 1) // 1 day in future

        val config = NotificationUtils.createTaskNotification(taskId, taskTitle, deadline)

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

        val config = NotificationUtils.createTaskNotification(taskId, taskTitle, deadline)

        assertEquals(NotificationCompat.PRIORITY_DEFAULT, config.priority)
    }

    @Test
    fun testCreateJoinRequestNotification() {
        val requestId = "req_1"
        val requesterName = "John Doe"
        val teamName = "Alpha Team"

        val config = NotificationUtils.createJoinRequestNotification(requestId, requesterName, teamName)

        assertEquals(NotificationUtils.TYPE_JOIN_REQUEST, config.type)
        assertEquals("👥 Team Join Request", config.title)
        assertEquals("$requesterName wants to join $teamName", config.message)
        assertEquals(NotificationCompat.PRIORITY_DEFAULT, config.priority)
        assertEquals(NotificationCompat.CATEGORY_SOCIAL, config.category)
        assertEquals(requestId, config.extras["requestId"])
        assertEquals(teamName, config.extras["teamName"])
    }

    @Test
    fun testCreateStorageWarningNotification() {
        val customId = "storage_1"
        val warningHigh = NotificationUtils.createStorageWarningNotification(96, customId)
        val warningLow = NotificationUtils.createStorageWarningNotification(90, customId)

        assertEquals(NotificationCompat.PRIORITY_HIGH, warningHigh.priority)
        assertEquals(NotificationCompat.PRIORITY_DEFAULT, warningLow.priority)
        assertEquals(NotificationUtils.TYPE_STORAGE, warningHigh.type)
        assertEquals("⚠️ Storage Warning", warningHigh.title)
        assertTrue(warningHigh.message.contains("96%"))
    }

    @Test
    fun testCreateResourceNotification() {
        val notificationId = "res_1"
        val resourceCount = 5

        val config = NotificationUtils.createResourceNotification(notificationId, resourceCount)

        assertEquals(NotificationUtils.TYPE_RESOURCE, config.type)
        assertEquals("📚 New Resources Available", config.title)
        assertTrue(config.message.contains("5 new resources"))
        assertEquals(NotificationCompat.CATEGORY_RECOMMENDATION, config.category)
        assertEquals("5", config.extras["resourceCount"])
    }

    @Test
    fun testCreateSummaryNotification() {
        val surveySummary = NotificationUtils.createSummaryNotification(NotificationUtils.TYPE_SURVEY, 3)
        assertEquals("summary_survey", surveySummary.id)
        assertEquals(NotificationUtils.TYPE_SURVEY, surveySummary.type)
        assertTrue(surveySummary.message.contains("3 new surveys"))

        val defaultSummary = NotificationUtils.createSummaryNotification("unknown_type", 10)
        assertEquals("summary_unknown_type", defaultSummary.id)
        assertEquals("unknown_type", defaultSummary.type)
        assertEquals("📱 App Notifications", defaultSummary.title)
        assertTrue(defaultSummary.message.contains("10 new notifications"))
    }

    @Test
    fun testNotificationManagerInit() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = mockk<android.content.SharedPreferences>()
        val editor = mockk<android.content.SharedPreferences.Editor>()

        val spyContext = spyk(context)

        every { spyContext.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.getStringSet(any(), any()) } returns emptySet()
        every { prefs.edit() } returns editor
        every { editor.putStringSet(any(), any()) } returns editor
        every { editor.apply() } returns Unit

        val notificationManagerMock = mockk<android.app.NotificationManager>(relaxed = true)
        every { spyContext.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManagerMock

        val manager = NotificationUtils.NotificationManager(spyContext)

        assertNotNull(manager)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            verify(atLeast = 1) { notificationManagerMock.createNotificationChannel(any()) }
        }
    }

    @Test
    fun testCreate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val spyContext = spyk(context)
        val manager = mockk<android.app.NotificationManager>(relaxed = true)

        every { spyContext.getSystemService(Context.NOTIFICATION_SERVICE) } returns manager

        NotificationUtils.create(spyContext, 123, "Test Title", "Test Text")

        verify { manager.notify(111, any()) }
    }
}
