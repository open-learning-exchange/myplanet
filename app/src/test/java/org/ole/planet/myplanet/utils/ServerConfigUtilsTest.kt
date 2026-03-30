package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerConfigUtilsTest {

    @Test
    fun getPinForUrl_returnsCorrectPinForKnownUrls() {
        // Asserting against literal string constants makes the test catch regressions
        // rather than just mirroring whatever BuildConfig happens to contain.
        assertEquals("1983", ServerConfigUtils.getPinForUrl("planet.learning.ole.org"))
        assertEquals("5562", ServerConfigUtils.getPinForUrl("planet.gt"))
        assertEquals("5234", ServerConfigUtils.getPinForUrl("192.168.48.253"))
        assertEquals("7379", ServerConfigUtils.getPinForUrl("planet.earth.ole.org"))
        assertEquals("5932", ServerConfigUtils.getPinForUrl("planet.somalia.ole.org"))
        assertEquals("0660", ServerConfigUtils.getPinForUrl("planet.vi.ole.org"))
        assertEquals("4324", ServerConfigUtils.getPinForUrl("10.82.1.31"))
        assertEquals("4025", ServerConfigUtils.getPinForUrl("192.168.1.64"))
        assertEquals("8925", ServerConfigUtils.getPinForUrl("192.168.1.93"))
        assertEquals("0963", ServerConfigUtils.getPinForUrl("192.168.1.148"))
        assertEquals("6407", ServerConfigUtils.getPinForUrl("192.168.68.126"))
    }

    @Test
    fun getPinForUrl_returnsEmptyStringForUnknownUrl() {
        assertEquals("", ServerConfigUtils.getPinForUrl("https://unknown.url"))
    }

    @Test
    fun getPinForUrl_returnsEmptyStringForEmptyUrl() {
        assertEquals("", ServerConfigUtils.getPinForUrl(""))
    }
}
