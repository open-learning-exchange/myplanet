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
}
