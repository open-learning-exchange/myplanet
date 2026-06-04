package org.ole.planet.myplanet.services

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DownloadServiceTest {

    private lateinit var mockPreferences: SharedPreferences

    @Before
    fun setUp() {
        mockPreferences = mockk()
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
        // Since getNextUrl sorts the URLs, 'file1' should be returned first
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
}
