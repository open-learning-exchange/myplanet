package org.ole.planet.myplanet.services

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.ContextCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class DownloadServiceTest {

    private lateinit var mockPreferences: SharedPreferences

    @Before
    fun setUp() {
        mockPreferences = mockk()
    }

    @After
    fun tearDown() {
        unmockkAll()
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

    // --- Tests for startService ---

    @Test
    fun `test startService starts foreground service when API is less than S`() {
        val mockContext = mockk<Context>(relaxed = true)
        mockkStatic(ContextCompat::class)
        every { ContextCompat.startForegroundService(any(), any()) } returns mockk()
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.R)

        DownloadService.startService(mockContext, "test_urls", false)

        verify { ContextCompat.startForegroundService(mockContext, any()) }
    }

    @Test
    fun `test startService starts foreground service for Activity context on Android S and above`() {
        val mockActivity: Activity = mockk(relaxed = true)
        mockkStatic(ContextCompat::class)
        every { ContextCompat.startForegroundService(any(), any()) } returns mockk()
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.S)

        DownloadService.startService(mockActivity, "test_urls", false)

        verify { ContextCompat.startForegroundService(mockActivity, any()) }
    }

    @Test
    fun `test startService starts foreground service on Android 14 when background restricted is false`() {
        val mockContext = mockk<Context>(relaxed = true)
        val mockActivityManager = mockk<ActivityManager>(relaxed = true)
        every { mockContext.getSystemService(Context.ACTIVITY_SERVICE) } returns mockActivityManager
        every { mockActivityManager.isBackgroundRestricted } returns false
        mockkStatic(ContextCompat::class)
        every { ContextCompat.startForegroundService(any(), any()) } returns mockk()
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.UPSIDE_DOWN_CAKE)

        DownloadService.startService(mockContext, "test_urls", false)

        verify { ContextCompat.startForegroundService(mockContext, any()) }
    }

    @Test
    fun `test startService starts worker on Android 14 when background restricted is true`() {
        val mockContext = mockk<Context>(relaxed = true)
        val mockActivityManager = mockk<ActivityManager>(relaxed = true)
        every { mockContext.getSystemService(Context.ACTIVITY_SERVICE) } returns mockActivityManager
        every { mockActivityManager.isBackgroundRestricted } returns true
        mockkStatic(ContextCompat::class)
        every { ContextCompat.startForegroundService(any(), any()) } returns mockk()
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.UPSIDE_DOWN_CAKE)

        DownloadService.startService(mockContext, "test_urls", false)

        verify(exactly = 0) { ContextCompat.startForegroundService(any(), any()) }
    }

    @Test
    fun `test startService fallbacks to worker if startForegroundService throws exception`() {
        val mockContext = mockk<Context>(relaxed = true)
        val mockActivityManager = mockk<ActivityManager>(relaxed = true)
        every { mockContext.getSystemService(Context.ACTIVITY_SERVICE) } returns mockActivityManager
        every { mockActivityManager.isBackgroundRestricted } returns false
        mockkStatic(ContextCompat::class)
        every { ContextCompat.startForegroundService(any(), any()) } throws IllegalStateException("Foreground exception")
        every { mockContext.startService(any()) } throws IllegalStateException("Service exception")
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.UPSIDE_DOWN_CAKE)

        DownloadService.startService(mockContext, "test_urls", false)

        verify { ContextCompat.startForegroundService(mockContext, any()) }
    }
}
