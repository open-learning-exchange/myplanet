package org.ole.planet.myplanet.utils

import android.net.Uri
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WebViewSafetyTest {

    private val trustedHosts = listOf("planet.learning.org", "myplanet.com")
    private val appDir = "/data/user/0/org.ole.planet.myplanet/files"

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val urlString = firstArg<String>()
            val schemeIndex = urlString.indexOf("://")
            val scheme = if (schemeIndex != -1) urlString.substring(0, schemeIndex)
            else if (urlString.startsWith("javascript:")) "javascript"
            else if (urlString.startsWith("data:")) "data"
            else null

            val host = if (schemeIndex != -1) {
                val afterScheme = urlString.substring(schemeIndex + 3)
                val pathIndex = afterScheme.indexOf("/")
                if (pathIndex != -1) afterScheme.substring(0, pathIndex) else afterScheme
            } else null

            val mockUri = io.mockk.mockk<Uri>()
            every { mockUri.scheme } returns scheme
            every { mockUri.host } returns host
            mockUri
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test https urls are allowed`() {
        assertTrue(WebViewSafety.isUrlSafe("https://example.com", trustedHosts, "resId", appDir))
        assertTrue(WebViewSafety.isUrlSafe("https://planet.learning.org", trustedHosts, null, ""))
    }

    @Test
    fun `test http urls are allowed only for trusted hosts`() {
        assertTrue(WebViewSafety.isUrlSafe("http://planet.learning.org", trustedHosts, null, ""))
        assertTrue(WebViewSafety.isUrlSafe("http://sub.planet.learning.org", trustedHosts, null, ""))
        assertFalse(WebViewSafety.isUrlSafe("http://untrusted.com", trustedHosts, null, ""))
    }

    @Test
    fun `test file urls are allowed for local resources`() {
        assertTrue(WebViewSafety.isUrlSafe("file://$appDir/image.png", trustedHosts, "res123", appDir))
    }

    @Test
    fun `test file urls are blocked if not in app directory`() {
        assertFalse(WebViewSafety.isUrlSafe("file:///sdcard/image.png", trustedHosts, "res123", appDir))
    }

    @Test
    fun `test file urls are blocked if resourceId is null`() {
        assertFalse(WebViewSafety.isUrlSafe("file://$appDir/image.png", trustedHosts, null, appDir))
    }

    @Test
    fun `test file urls are blocked if appDir is empty`() {
        assertFalse(WebViewSafety.isUrlSafe("file://$appDir/image.png", trustedHosts, "res123", ""))
    }

    @Test
    fun `test other schemes are blocked`() {
        assertFalse(WebViewSafety.isUrlSafe("ftp://server.com", trustedHosts, null, ""))
        assertFalse(WebViewSafety.isUrlSafe("javascript:alert(1)", trustedHosts, null, ""))
        assertFalse(WebViewSafety.isUrlSafe("data:text/html,<html></html>", trustedHosts, null, ""))
    }

    @Test
    fun `test malformed urls handle exception and return false`() {
        assertFalse(WebViewSafety.isUrlSafe(":", trustedHosts, null, ""))
    }

    @Test
    fun `test isTrustedPlanetServer match exact host`() {
        assertTrue(WebViewSafety.isTrustedPlanetServer("planet.learning.org", trustedHosts))
    }

    @Test
    fun `test isTrustedPlanetServer match subdomain`() {
        assertTrue(WebViewSafety.isTrustedPlanetServer("api.planet.learning.org", trustedHosts))
    }

    @Test
    fun `test isTrustedPlanetServer does not match partial suffix`() {
        assertFalse(WebViewSafety.isTrustedPlanetServer("notmyplanet.com", trustedHosts))
    }

    @Test
    fun `test isTrustedPlanetServer does not match untrusted host`() {
        assertFalse(WebViewSafety.isTrustedPlanetServer("google.com", trustedHosts))
    }

    @Test
    fun `test isTrustedPlanetServer handles null host`() {
        assertFalse(WebViewSafety.isTrustedPlanetServer(null, trustedHosts))
    }
}
