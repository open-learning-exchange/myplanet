package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadUtilsTest {

    @Test
    fun extractLinks_returnsEmptyListForNullInput() {
        val result = DownloadUtils.extractLinks(null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun extractLinks_returnsEmptyListForEmptyInput() {
        val result = DownloadUtils.extractLinks("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun extractLinks_returnsEmptyListWhenNoMatchesFound() {
        val text = "This is some text without any image links."
        val result = DownloadUtils.extractLinks(text)
        assertTrue(result.isEmpty())
    }

    @Test
    fun extractLinks_extractsSingleImageLink() {
        val text = "Check out this image: ![alt text](https://example.com/image.png)"
        val result = DownloadUtils.extractLinks(text)
        assertEquals(1, result.size)
        assertEquals("https://example.com/image.png", result[0])
    }

    @Test
    fun extractLinks_extractsMultipleImageLinks() {
        val text = "Here are two images: ![first](https://example.com/1.png) and ![second](https://example.com/2.png)"
        val result = DownloadUtils.extractLinks(text)
        assertEquals(2, result.size)
        assertEquals("https://example.com/1.png", result[0])
        assertEquals("https://example.com/2.png", result[1])
    }

    @Test
    fun extractLinks_ignoresRegularMarkdownLinks() {
        val text = "This is a [regular link](https://example.com) and this is an ![image](https://example.com/img.png)"
        val result = DownloadUtils.extractLinks(text)
        assertEquals(1, result.size)
        assertEquals("https://example.com/img.png", result[0])
    }

    @Test
    fun extractLinks_ignoresEmptyImageLinks() {
        val text = "An empty image link: ![alt]()"
        val result = DownloadUtils.extractLinks(text)
        assertTrue(result.isEmpty())
    }
}
