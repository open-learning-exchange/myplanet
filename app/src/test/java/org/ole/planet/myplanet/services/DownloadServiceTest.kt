package org.ole.planet.myplanet.services

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkRequest
import androidx.work.impl.WorkManagerImpl
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import java.lang.reflect.Field
import javax.inject.Provider
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.utils.DownloadUtils
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = Application::class)
class DownloadServiceTest {

    private lateinit var mockPreferences: SharedPreferences

    @Before
    fun setUp() {
        mockPreferences = mockk(relaxed = true)
        mockkStatic(ContextCompat::class)
        mockkObject(DownloadUtils)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.i(any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
        // Reset SDK version for other tests that might run in same VM
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 0)
    }

    // --- Tests for startService ---

    @Test
    fun `test startService starts foreground service when API is less than S`() {
        val mContext = mockk<Context>(relaxed = true)
        every { ContextCompat.startForegroundService(any(), any()) } returns mockk()
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.R)

        DownloadService.startService(mContext, "test_urls", false)

        verify { ContextCompat.startForegroundService(mContext, any()) }
    }

    @Test
    fun `test startService starts foreground service for Activity context on Android S and above`() {
        val mockActivity: Activity = mockk(relaxed = true)
        every { ContextCompat.startForegroundService(any(), any()) } returns mockk()
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.S)

        DownloadService.startService(mockActivity, "test_urls", false)

        verify { ContextCompat.startForegroundService(mockActivity, any()) }
    }

    @Test
    fun `test startService starts foreground service on Android 14 when background restricted is false`() {
        val applicationContext = ApplicationProvider.getApplicationContext<Context>()
        val mockActivityManager = mockk<ActivityManager>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns applicationContext
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns mockActivityManager
        every { mockActivityManager.isBackgroundRestricted } returns false
        every { ContextCompat.startForegroundService(any(), any()) } returns mockk()
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.UPSIDE_DOWN_CAKE)

        DownloadService.startService(context, "test_urls", false)

        verify { ContextCompat.startForegroundService(context, any()) }
    }

    @Test
    fun `test startService enqueues worker on Android 14 when background restricted is true`() {
        val mockActivityManager = mockk<ActivityManager>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        val app = ApplicationProvider.getApplicationContext<Application>()

        val mockWorkManager = mockk<WorkManagerImpl>(relaxed = true)
        mockkStatic(WorkManagerImpl::class)
        every { WorkManagerImpl.getInstance(any()) } returns mockWorkManager
        val slotRequest = slot<WorkRequest>()
        every { mockWorkManager.enqueue(capture(slotRequest)) } returns mockk()

        every { context.applicationContext } returns app
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns mockActivityManager
        every { mockActivityManager.isBackgroundRestricted } returns true

        every { ContextCompat.startForegroundService(any(), any()) } returns mockk()

        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.UPSIDE_DOWN_CAKE)

        DownloadService.startService(context, "test_urls_from_background", false)

        verify(exactly = 0) { ContextCompat.startForegroundService(any(), any()) }
        verify { mockWorkManager.enqueue(any<WorkRequest>()) }

        val req = slotRequest.captured as OneTimeWorkRequest
        assertEquals("test_urls_from_background", req.workSpec.input.getString("urls_key"))
        assertEquals(false, req.workSpec.input.getBoolean("fromSync", true))
    }

    @Test
    fun `test startService enqueues worker if startForegroundService throws exception`() {
        val mockActivityManager = mockk<ActivityManager>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        val app = ApplicationProvider.getApplicationContext<Application>()

        val mockWorkManager = mockk<WorkManagerImpl>(relaxed = true)
        mockkStatic(WorkManagerImpl::class)
        every { WorkManagerImpl.getInstance(any()) } returns mockWorkManager
        val slotRequest = slot<WorkRequest>()
        every { mockWorkManager.enqueue(capture(slotRequest)) } returns mockk()

        every { context.applicationContext } returns app
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns mockActivityManager
        every { mockActivityManager.isBackgroundRestricted } returns false

        // Throw exception to trigger fallback
        every { ContextCompat.startForegroundService(any(), any()) } throws IllegalStateException("Foreground exception")
        every { context.startService(any()) } throws IllegalStateException("Service exception")

        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.UPSIDE_DOWN_CAKE)

        try {
            DownloadService.startService(context, "test_urls_fallback", true)
        } catch (e: Exception) {
            // expected
        }

        verify { ContextCompat.startForegroundService(any(), any()) }
        verify { mockWorkManager.enqueue(any<WorkRequest>()) }

        val req = slotRequest.captured as OneTimeWorkRequest
        assertEquals("test_urls_fallback", req.workSpec.input.getString("urls_key"))
        assertEquals(true, req.workSpec.input.getBoolean("fromSync", false))
    }

    @Test
    fun `test queueRunning is reset when exception occurs`() = runBlocking {
        val service = spyk(DownloadService())

        val notificationManager = mockk<NotificationManager>(relaxed = true)
        every { service.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager
        every { service.startForeground(any(), any()) } returns Unit

        every { service.packageName } returns "org.ole.planet.myplanet"
        every { service.applicationInfo } returns mockk(relaxed = true)

        every { DownloadUtils.createChannels(any()) } returns Unit
        every { DownloadUtils.buildInitialNotification(any()) } returns mockk(relaxed = true)

        val job = SupervisorJob()
        val exceptionHandler = CoroutineExceptionHandler { _, _ -> }

        val testScope = CoroutineScope(job + Dispatchers.IO + exceptionHandler)

        val prefsField: Field = DownloadService::class.java.getDeclaredField("preferencesProvider")
        prefsField.isAccessible = true
        prefsField.set(service, Provider { mockPreferences })

        val appScopeField: Field = DownloadService::class.java.getDeclaredField("appScope")
        appScopeField.isAccessible = true
        appScopeField.set(service, testScope)

        every { mockPreferences.getStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, any()) } throws RuntimeException("Simulated coroutine error")

        val intent = mockk<Intent>(relaxed = true)

        // This launches the coroutine asynchronously
        service.onStartCommand(intent, 0, 1)

        // Wait for coroutines to complete
        job.children.forEach { it.join() }

        val isQueueRunningField: Field = DownloadService::class.java.getDeclaredField("isQueueRunning")
        isQueueRunningField.isAccessible = true
        val isQueueRunning = isQueueRunningField.get(service) as Boolean

        assertFalse("isQueueRunning should be false after exception", isQueueRunning)
    }

    @Test
    fun `test sendNotification uses cached count and avoids SharedPreferences and NotificationManagerCompat checks`() {
        val service = spyk(DownloadService())

        // Set up field values
        val currentDownloadUrlField = DownloadService::class.java.getDeclaredField("currentDownloadUrl")
        currentDownloadUrlField.isAccessible = true
        currentDownloadUrlField.set(service, "http://example.com/file.pdf")

        val cachedRemainingCountField = DownloadService::class.java.getDeclaredField("cachedRemainingCount")
        cachedRemainingCountField.isAccessible = true
        cachedRemainingCountField.set(service, 5)

        val areNotificationsEnabledField = DownloadService::class.java.getDeclaredField("areNotificationsEnabled")
        areNotificationsEnabledField.isAccessible = true
        areNotificationsEnabledField.set(service, true)

        val sessionCompletedCountField = DownloadService::class.java.getDeclaredField("sessionCompletedCount")
        sessionCompletedCountField.isAccessible = true
        sessionCompletedCountField.set(service, 2)

        val currentFileProgressField = DownloadService::class.java.getDeclaredField("currentFileProgress")
        currentFileProgressField.isAccessible = true
        currentFileProgressField.set(service, 50)

        val mockNotificationManager = mockk<NotificationManager>(relaxed = true)
        val notificationManagerField = DownloadService::class.java.getDeclaredField("notificationManager")
        notificationManagerField.isAccessible = true
        notificationManagerField.set(service, mockNotificationManager)

        val mockBuilder = mockk<NotificationCompat.Builder>(relaxed = true)
        every { mockBuilder.build() } returns mockk()
        val notificationBuilderField = DownloadService::class.java.getDeclaredField("notificationBuilder")
        notificationBuilderField.isAccessible = true
        notificationBuilderField.set(service, mockBuilder)

        val mockBroadcast = mockk<BroadcastService>(relaxed = true)
        val broadcastServiceField = DownloadService::class.java.getDeclaredField("broadcastService")
        broadcastServiceField.isAccessible = true
        broadcastServiceField.set(service, mockBroadcast)

        val appScopeField = DownloadService::class.java.getDeclaredField("appScope")
        appScopeField.isAccessible = true
        appScopeField.set(service, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))

        mockkStatic(NotificationManagerCompat::class)

        val download = Download()
        val sendNotificationMethod = DownloadService::class.java.getDeclaredMethod("sendNotification", Download::class.java)
        sendNotificationMethod.isAccessible = true
        sendNotificationMethod.invoke(service, download)

        verify(exactly = 0) { mockPreferences.getStringSet(any(), any()) }
        verify(exactly = 0) { NotificationManagerCompat.from(any()) }
        verify { mockBuilder.setSubText("2 completed, 5 remaining") }
    }

    @Test
    fun `test updateNotificationForBatchDownload reuses existing notificationBuilder`() {
        val service = spyk(DownloadService())

        val context = ApplicationProvider.getApplicationContext<Context>()
        val mockNotificationManager = mockk<NotificationManager>(relaxed = true)
        val notificationManagerField = DownloadService::class.java.getDeclaredField("notificationManager")
        notificationManagerField.isAccessible = true
        notificationManagerField.set(service, mockNotificationManager)

        val existingBuilder = spyk(NotificationCompat.Builder(context, "DownloadChannel"))
        val notificationBuilderField = DownloadService::class.java.getDeclaredField("notificationBuilder")
        notificationBuilderField.isAccessible = true
        notificationBuilderField.set(service, existingBuilder)

        val cachedRemainingCountField = DownloadService::class.java.getDeclaredField("cachedRemainingCount")
        cachedRemainingCountField.isAccessible = true
        cachedRemainingCountField.set(service, 3)

        val updateMethod = DownloadService::class.java.getDeclaredMethod("updateNotificationForBatchDownload")
        updateMethod.isAccessible = true
        updateMethod.invoke(service)

        val resultBuilder = notificationBuilderField.get(service) as NotificationCompat.Builder
        assertSame("Existing notification builder instance should be reused", existingBuilder, resultBuilder)
        verify { existingBuilder.setContentText("Starting downloads (0/4)") }
    }
}
