package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
class NetworkUtilsTest {

    @Test
    fun extractProtocol_withHttpUrl() {
        assertEquals("http://", NetworkUtils.extractProtocol("http://example.com"))
    }

    @Test
    fun extractProtocol_withHttpsUrl() {
        assertEquals("https://", NetworkUtils.extractProtocol("https://example.com/path"))
    }

    @Test
    fun extractProtocol_withFtpUrl() {
        assertEquals("ftp://", NetworkUtils.extractProtocol("ftp://192.168.1.1"))
    }

    @Test
    fun extractProtocol_withCustomScheme() {
        assertEquals("custom://", NetworkUtils.extractProtocol("custom://app"))
    }

    @Test
    fun extractProtocol_withNoScheme() {
        assertNull(NetworkUtils.extractProtocol("example.com"))
    }

    @Test
    fun extractProtocol_withRelativeUrl() {
        assertNull(NetworkUtils.extractProtocol("//example.com"))
    }

    @Test
    fun extractProtocol_withEmptyString() {
        assertNull(NetworkUtils.extractProtocol(""))
    }
}
