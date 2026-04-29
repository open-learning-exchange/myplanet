package org.ole.planet.myplanet.utils

import android.content.Context
import android.net.wifi.WifiManager
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
class NetworkUtilsTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun init() {
        hiltRule.inject()
        // Initialize MainApplication.context which is required by NetworkUtils to avoid UninitializedPropertyAccessException
        // It is needed here because NetworkUtils gets system service from it directly
        org.ole.planet.myplanet.MainApplication.context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testIsWifiEnabled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

        wifiManager.isWifiEnabled = true
        assertTrue(NetworkUtils.isWifiEnabled())

        wifiManager.isWifiEnabled = false
        assertFalse(NetworkUtils.isWifiEnabled())
    }

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

    @Test
    fun extractProtocol_withSpaces() {
        assertEquals("http://", NetworkUtils.extractProtocol("  http://example.com  "))
    }

    @Test
    fun extractProtocol_withMalformedUrl() {
        assertNull(NetworkUtils.extractProtocol("htt p://example.com"))
    }

    @Test
    fun extractProtocol_withInvalidSchemeCharacters() {
        assertEquals("http#://", NetworkUtils.extractProtocol("http#://example.com"))
    }

    @Test
    fun extractProtocol_withHttpUrlWithoutDomain() {
        assertEquals("http://", NetworkUtils.extractProtocol("http://"))
    }
}
