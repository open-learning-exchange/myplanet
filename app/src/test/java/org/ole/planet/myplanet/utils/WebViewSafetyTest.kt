package org.ole.planet.myplanet.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewSafetyTest {

    private val trustedHosts = listOf(
        "trusted.com",
        "also.trusted.org"
    )

    @Test
    fun isTrustedPlanetServer_trustedHosts() {
        assertTrue(WebViewSafety.isTrustedPlanetServer("trusted.com", trustedHosts))
        assertTrue(WebViewSafety.isTrustedPlanetServer("sub.trusted.com", trustedHosts))
        assertTrue(WebViewSafety.isTrustedPlanetServer("also.trusted.org", trustedHosts))
    }

    @Test
    fun isTrustedPlanetServer_untrustedHosts() {
        assertFalse(WebViewSafety.isTrustedPlanetServer("untrusted.com", trustedHosts))
        assertFalse(WebViewSafety.isTrustedPlanetServer("nottrusted.com", trustedHosts))
        assertFalse(WebViewSafety.isTrustedPlanetServer("fake-trusted.com", trustedHosts))
        assertFalse(WebViewSafety.isTrustedPlanetServer(null, trustedHosts))
    }

    @Test
    fun isUrlSafe_https_alwaysAllowed() {
        assertTrue(WebViewSafety.isUrlSafe("https://google.com", trustedHosts, null, ""))
        assertTrue(WebViewSafety.isUrlSafe("https://anything.com", trustedHosts, null, ""))
    }

    @Test
    fun isUrlSafe_http_onlyTrusted() {
        assertTrue(WebViewSafety.isUrlSafe("http://trusted.com", trustedHosts, null, ""))
        assertTrue(WebViewSafety.isUrlSafe("http://sub.trusted.com/path", trustedHosts, null, ""))
        assertFalse(WebViewSafety.isUrlSafe("http://untrusted.com", trustedHosts, null, ""))
    }

    @Test
    fun isUrlSafe_file_onlyWithResourceIdAndAppDir() {
        val appDir = "/data/user/0/org.ole.planet.myplanet/files"

        // Allowed: resourceId present, appDir present, url starts with appDir
        assertTrue(WebViewSafety.isUrlSafe("file://$appDir/ole/resource/index.html", trustedHosts, "resource1", appDir))

        // Denied: resourceId missing
        assertFalse(WebViewSafety.isUrlSafe("file://$appDir/ole/resource/index.html", trustedHosts, null, appDir))

        // Denied: appDir empty
        assertFalse(WebViewSafety.isUrlSafe("file://$appDir/ole/resource/index.html", trustedHosts, "resource1", ""))

        // Denied: url not in appDir
        assertFalse(WebViewSafety.isUrlSafe("file:///etc/passwd", trustedHosts, "resource1", appDir))
    }

    @Test
    fun isUrlSafe_otherSchemes_denied() {
        assertFalse(WebViewSafety.isUrlSafe("ftp://trusted.com", trustedHosts, null, ""))
        assertFalse(WebViewSafety.isUrlSafe("javascript:alert(1)", trustedHosts, null, ""))
    }
}
