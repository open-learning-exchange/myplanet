package org.ole.planet.myplanet.services

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
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
import org.ole.planet.myplanet.utils.DownloadUtils
import java.lang.reflect.Field

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadServiceTest {

    private lateinit var mockPreferences: SharedPreferences

    @Before
    fun setUp() {
        mockPreferences = mockk(relaxed = true)
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
        unmockkObject(DownloadUtils)
        unmockkStatic(Log::class)
    }

    // --- Tests for getNextUrl (testing both Priority and Pending paths) ---

    @Test
    fun `test getNextUrl returns null when no urls`() {
        every { mockPreferences.getStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, emptySet()) } returns emptySet()
        val result = DownloadService.getNextUrl(mockPreferences, DownloadService.PRIORITY_DOWNLOADS_KEY, emptySet(), true)
        assertNull(result)
    }

    @Test
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
