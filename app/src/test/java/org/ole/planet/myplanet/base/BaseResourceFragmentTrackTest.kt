package org.ole.planet.myplanet.base

import java.lang.reflect.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseResourceFragmentTrackTest {

    @Test
    fun `trackDownloadUrls should clear existing and store new urls in pendingDownloadUrls`() {
        // Arrange
        val fragment = object : BaseResourceFragment() {
            // Expose protected method
            fun callTrack(urls: Collection<String>) {
                trackDownloadUrls(urls)
            }
        }

        val initialUrls = listOf("http://example.com/old_file")
        val newUrls = listOf("http://example.com/file1", "http://example.com/file2")

        val field: Field = BaseResourceFragment::class.java.getDeclaredField("pendingDownloadUrls")
        field.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val pendingDownloadUrls = field.get(fragment) as MutableSet<String>

        // Populate initial data to test the clear() functionality
        pendingDownloadUrls.addAll(initialUrls)

        // Act
        fragment.callTrack(newUrls)

        // Assert
        assertEquals(2, pendingDownloadUrls.size)
        assertTrue(pendingDownloadUrls.contains("http://example.com/file1"))
        assertTrue(pendingDownloadUrls.contains("http://example.com/file2"))
        assertEquals(false, pendingDownloadUrls.contains("http://example.com/old_file"))
    }
}
