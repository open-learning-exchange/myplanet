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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
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

    // --- Tests for getNextUrl (testing both Priority and Pending paths) ---

    @Test
    fun `test getNextUrl returns null when no urls`() {
        every { mockPreferences.getStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, emptySet()) } returns emptySet()
        val result = DownloadService.getNextUrl(mockPreferences, DownloadService.PRIORITY_DOWNLOADS_KEY, emptySet(), true)
        assertNull(result)
    }

    @Test
    // Since getNextUrl sorts the URLs, we expect 'http://example.com/file1' to be picked first deterministically.
    fun `test getNextUrl returns first valid url deterministically`() {
        val urlSet = setOf("http://example.com/file2", "http://example.com/file1")
        every { mockPreferences.getStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, emptySet()) } returns urlSet

        val result = DownloadService.getNextUrl(mockPreferences, DownloadService.PRIORITY_DOWNLOADS_KEY, emptySet(), true)
        assertNotNull(result)

        assertEquals("http://example.com/file1", result?.url)
        assertEquals(true, result?.isPriority)
    }

    @Test
    fun `test getNextUrl skips processed urls`() {
        val urlSet = setOf("http://example.com/file1", "http://example.com/file2")
        every { mockPreferences.getStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, emptySet()) } returns urlSet

        val processedUrls = setOf("http://example.com/file1")
        val result = DownloadService.getNextUrl(mockPreferences, DownloadService.PRIORITY_DOWNLOADS_KEY, processedUrls, true)

        assertNotNull(result)
        assertEquals("http://example.com/file2", result?.url)
    }

    @Test
    fun `test getNextUrl returns null if all urls are processed`() {
        val urlSet = setOf("http://example.com/file1", "http://example.com/file2")
        every { mockPreferences.getStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, emptySet()) } returns urlSet

        val processedUrls = setOf("http://example.com/file1", "http://example.com/file2")
        val result = DownloadService.getNextUrl(mockPreferences, DownloadService.PRIORITY_DOWNLOADS_KEY, processedUrls, true)

        assertNull(result)
    }

    @Test
    fun `test getNextUrl skips blank urls`() {
        val urlSet = setOf("   ", "http://example.com/file1")
        every { mockPreferences.getStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, emptySet()) } returns urlSet

        val result = DownloadService.getNextUrl(mockPreferences, DownloadService.PRIORITY_DOWNLOADS_KEY, emptySet(), true)
        assertNotNull(result)
        assertEquals("http://example.com/file1", result?.url)
    }

    @Test
    fun `test getNextUrl handles pending urls correctly`() {
        val urlSet = setOf("http://example.com/file2", "http://example.com/file1")
        every { mockPreferences.getStringSet(DownloadService.PENDING_DOWNLOADS_KEY, emptySet()) } returns urlSet

        val result = DownloadService.getNextUrl(mockPreferences, DownloadService.PENDING_DOWNLOADS_KEY, emptySet(), false)
        assertNotNull(result)

        assertEquals("http://example.com/file1", result?.url)
        assertEquals(false, result?.isPriority)
    }

    // --- Tests for getNextPriorityUrl ---

    @Test
    fun `test getNextPriorityUrl returns null when queue is empty`() {
        val result = DownloadService.getNextPriorityUrl(emptyList())
        assertNull(result)
    }

    @Test
    fun `test getNextPriorityUrl returns single item`() {
        val queue = listOf(DownloadService.QueuedUrl("url1", true, 5))
        val result = DownloadService.getNextPriorityUrl(queue)
        assertNotNull(result)
        assertEquals("url1", result?.url)
        assertEquals(5, result?.priority)
    }

    @Test
    fun `test getNextPriorityUrl returns item with highest priority`() {
        val queue = listOf(
            DownloadService.QueuedUrl("url1", true, 5),
            DownloadService.QueuedUrl("url2", true, 10),
            DownloadService.QueuedUrl("url3", true, 3)
        )
        val result = DownloadService.getNextPriorityUrl(queue)
        assertNotNull(result)
        assertEquals("url2", result?.url)
        assertEquals(10, result?.priority)
    }

    @Test
    fun `test getNextPriorityUrl returns first item if multiple have same max priority`() {
        val queue = listOf(
            DownloadService.QueuedUrl("url1", true, 10),
            DownloadService.QueuedUrl("url2", true, 10),
            DownloadService.QueuedUrl("url3", true, 5)
        )
        val result = DownloadService.getNextPriorityUrl(queue)
        assertNotNull(result)
        assertEquals("url1", result?.url)
        assertEquals(10, result?.priority)
    }

    // --- Tests for startService ---

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `test startService starts foreground service when API is less than S`() {
        val mContext = mockk<Context>(relaxed = true)
        every { ContextCompat.startForegroundService(any(), any()) } returns mockk()
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.R)

        DownloadService.startService(mContext, "test_urls", false)

        verify { ContextCompat.startForegroundService(mContext, any()) }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
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

        val prefsField: Field = DownloadService::class.java.getDeclaredField("preferences")
        prefsField.isAccessible = true
        prefsField.set(service, mockPreferences)

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
}
