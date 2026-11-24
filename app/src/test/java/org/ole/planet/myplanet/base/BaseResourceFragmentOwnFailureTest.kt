package org.ole.planet.myplanet.base

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.model.Download

class BaseResourceFragmentOwnFailureTest {

    private val fragment = object : BaseResourceFragment() {
        fun track(urls: Collection<String>) = trackDownloadUrls(urls)
    }.apply { track(listOf("https://planet/resources/math-game/index.html")) }

    private fun failure(url: String?) = Download().apply {
        failed = true
        message = "Software caused connection abort"
        fileUrl = url
    }

    @Test
    fun `failure of a file the screen is downloading is its own`() {
        assertTrue(fragment.isOwnFailure(failure("https://planet/resources/math-game/index.html")))
    }

    @Test
    fun `failure of a sync-queued file is not the screen's failure`() {
        assertFalse(fragment.isOwnFailure(failure("https://planet/courses/abc/cover.jpg")))
    }

    @Test
    fun `failure naming no file still ends the screen's download`() {
        assertTrue(fragment.isOwnFailure(failure(null)))
    }
}
