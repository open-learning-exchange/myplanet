package org.ole.planet.myplanet.utils

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
