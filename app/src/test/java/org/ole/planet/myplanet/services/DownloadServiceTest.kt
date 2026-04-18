package org.ole.planet.myplanet.services

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Method

class DownloadServiceTest {

    private lateinit var downloadService: DownloadService
    private lateinit var mockPreferences: SharedPreferences
    private lateinit var getNextPriorityUrlMethod: Method
    private lateinit var getNextPendingUrlMethod: Method

    @Before
    fun setUp() {
        mockPreferences = mockk()
        downloadService = DownloadService()

        // Inject preferences
        val prefField = DownloadService::class.java.getDeclaredField("preferences")
        prefField.isAccessible = true
        prefField.set(downloadService, mockPreferences)

        // Setup reflection for private methods
        getNextPriorityUrlMethod = DownloadService::class.java.getDeclaredMethod("getNextPriorityUrl")
        getNextPriorityUrlMethod.isAccessible = true

        getNextPendingUrlMethod = DownloadService::class.java.getDeclaredMethod("getNextPendingUrl")
        getNextPendingUrlMethod.isAccessible = true
    }

    private fun invokeGetNextPriorityUrl(): Any? {
        return getNextPriorityUrlMethod.invoke(downloadService)
    }

    private fun invokeGetNextPendingUrl(): Any? {
        return getNextPendingUrlMethod.invoke(downloadService)
    }

    // --- Tests for getNextPriorityUrl ---

    @Test
    fun `test getNextPriorityUrl returns null when no urls`() {
        every { mockPreferences.getStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, emptySet()) } returns emptySet()
        val result = invokeGetNextPriorityUrl()
        assertNull(result)
    }

    @Test
    fun `test getNextPriorityUrl returns first valid url`() {
        val urlSet = setOf("http://example.com/file1", "http://example.com/file2")
        every { mockPreferences.getStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, emptySet()) } returns urlSet

        val result = invokeGetNextPriorityUrl()
        assertNotNull(result)

        val url = result?.javaClass?.getDeclaredMethod("getUrl")?.invoke(result) as String
        val isPriority = result.javaClass.getDeclaredMethod("isPriority").invoke(result) as Boolean

        assertTrue(urlSet.contains(url))
        assertTrue(isPriority)
    }

    @Test
    fun `test getNextPriorityUrl skips processed urls`() {
        val urlSet = setOf("http://example.com/file1", "http://example.com/file2")
        every { mockPreferences.getStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, emptySet()) } returns urlSet

        // Mock processed urls
        val processedField = DownloadService::class.java.getDeclaredField("processedUrls")
        processedField.isAccessible = true
        val processedUrls = mutableSetOf("http://example.com/file1")
        processedField.set(downloadService, processedUrls)

        val result = invokeGetNextPriorityUrl()
        assertNotNull(result)

        val url = result?.javaClass?.getDeclaredMethod("getUrl")?.invoke(result) as String
        assertEquals("http://example.com/file2", url)
    }

    @Test
    fun `test getNextPriorityUrl skips blank urls`() {
        val urlSet = setOf("   ", "http://example.com/file1")
        every { mockPreferences.getStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, emptySet()) } returns urlSet

        val result = invokeGetNextPriorityUrl()
        assertNotNull(result)

        val url = result?.javaClass?.getDeclaredMethod("getUrl")?.invoke(result) as String
        assertEquals("http://example.com/file1", url)
    }

    // --- Tests for getNextPendingUrl ---

    @Test
    fun `test getNextPendingUrl returns null when no urls`() {
        every { mockPreferences.getStringSet(DownloadService.PENDING_DOWNLOADS_KEY, emptySet()) } returns emptySet()
        val result = invokeGetNextPendingUrl()
        assertNull(result)
    }

    @Test
    fun `test getNextPendingUrl returns first valid url`() {
        val urlSet = setOf("http://example.com/file1", "http://example.com/file2")
        every { mockPreferences.getStringSet(DownloadService.PENDING_DOWNLOADS_KEY, emptySet()) } returns urlSet

        val result = invokeGetNextPendingUrl()
        assertNotNull(result)

        val url = result?.javaClass?.getDeclaredMethod("getUrl")?.invoke(result) as String
        val isPriority = result.javaClass.getDeclaredMethod("isPriority").invoke(result) as Boolean

        assertTrue(urlSet.contains(url))
        assertFalse(isPriority)
    }

    @Test
    fun `test getNextPendingUrl skips processed urls`() {
        val urlSet = setOf("http://example.com/file1", "http://example.com/file2")
        every { mockPreferences.getStringSet(DownloadService.PENDING_DOWNLOADS_KEY, emptySet()) } returns urlSet

        // Mock processed urls
        val processedField = DownloadService::class.java.getDeclaredField("processedUrls")
        processedField.isAccessible = true
        val processedUrls = mutableSetOf("http://example.com/file1")
        processedField.set(downloadService, processedUrls)

        val result = invokeGetNextPendingUrl()
        assertNotNull(result)

        val url = result?.javaClass?.getDeclaredMethod("getUrl")?.invoke(result) as String
        assertEquals("http://example.com/file2", url)
    }

    @Test
    fun `test getNextPendingUrl skips blank urls`() {
        val urlSet = setOf("   ", "http://example.com/file1")
        every { mockPreferences.getStringSet(DownloadService.PENDING_DOWNLOADS_KEY, emptySet()) } returns urlSet

        val result = invokeGetNextPendingUrl()
        assertNotNull(result)

        val url = result?.javaClass?.getDeclaredMethod("getUrl")?.invoke(result) as String
        assertEquals("http://example.com/file1", url)
    }
}
