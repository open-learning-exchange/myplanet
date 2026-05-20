package org.ole.planet.myplanet.utils

import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.services.SharedPrefManager
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ServerConfigUtilsTest {

    private lateinit var mockSharedPrefManager: SharedPrefManager

    @Before
    fun setup() {
        mockSharedPrefManager = mockk(relaxed = true)
    }

    @Test
    fun testSaveAlternativeUrl_WithoutUserInfo() {
        val url = "http://demo.ole.org"
        val password = "testPassword"

        val result = ServerConfigUtils.saveAlternativeUrl(url, password, mockSharedPrefManager)

        val expectedDbUrl = "http://satellite:testPassword@demo.ole.org:80"
        assertEquals(expectedDbUrl, result)

        verify { mockSharedPrefManager.setServerPin(password) }
        verify { mockSharedPrefManager.setUrlUser("satellite") }
        verify { mockSharedPrefManager.setUrlPwd(password) }
        verify { mockSharedPrefManager.setUrlScheme("http") }
        verify { mockSharedPrefManager.setUrlHost("demo.ole.org") }
        verify { mockSharedPrefManager.setAlternativeUrl(url) }
        verify { mockSharedPrefManager.setProcessedAlternativeUrl(expectedDbUrl) }
        verify { mockSharedPrefManager.setIsAlternativeUrl(true) }
    }

    @Test
    fun testSaveAlternativeUrl_WithUserInfo() {
        val url = "http://admin:admin123@demo.ole.org:5984"
        val password = "someOtherPassword"

        val result = ServerConfigUtils.saveAlternativeUrl(url, password, mockSharedPrefManager)

        assertEquals(url, result)

        // Note: The pin uses the provided password parameter, while the URL password ("admin123") is extracted for setUrlPwd.
        verify { mockSharedPrefManager.setServerPin(password) }
        verify { mockSharedPrefManager.setUrlUser("admin") }
        verify { mockSharedPrefManager.setUrlPwd("admin123") }
        verify { mockSharedPrefManager.setUrlScheme("http") }
        verify { mockSharedPrefManager.setUrlHost("demo.ole.org") }
        verify { mockSharedPrefManager.setAlternativeUrl(url) }
        verify { mockSharedPrefManager.setProcessedAlternativeUrl(url) }
        verify { mockSharedPrefManager.setIsAlternativeUrl(true) }
    }

    @Test
    fun testSaveAlternativeUrl_HttpsDefaultPort() {
        val url = "https://demo.ole.org"
        val password = "testPassword"

        val result = ServerConfigUtils.saveAlternativeUrl(url, password, mockSharedPrefManager)

        val expectedDbUrl = "https://satellite:testPassword@demo.ole.org:443"
        assertEquals(expectedDbUrl, result)

        verify { mockSharedPrefManager.setUrlScheme("https") }
        verify { mockSharedPrefManager.setProcessedAlternativeUrl(expectedDbUrl) }
    }

    @Test
    fun testSaveAlternativeUrl_WithExplicitPort() {
        val url = "http://demo.ole.org:5984"
        val password = "testPassword"

        val result = ServerConfigUtils.saveAlternativeUrl(url, password, mockSharedPrefManager)

        val expectedDbUrl = "http://satellite:testPassword@demo.ole.org:5984"
        assertEquals(expectedDbUrl, result)

        verify { mockSharedPrefManager.setUrlHost("demo.ole.org") }
        verify { mockSharedPrefManager.setProcessedAlternativeUrl(expectedDbUrl) }
    }

    @Test
    fun testSaveAlternativeUrl_MalformedUrlWithEmptyUserInfo() {
        val url = "http://@demo.ole.org"
        val password = "testPassword"

        val result = ServerConfigUtils.saveAlternativeUrl(url, password, mockSharedPrefManager)

        assertEquals(url, result)

        verify { mockSharedPrefManager.setUrlUser("") }
        verify { mockSharedPrefManager.setUrlPwd("") }
    }

    @Test
    fun testSaveAlternativeUrl_MissingSchemeAndHost() {
        val url = "demo.ole.org"
        val password = "testPassword"

        val result = ServerConfigUtils.saveAlternativeUrl(url, password, mockSharedPrefManager)

        verify { mockSharedPrefManager.setUrlScheme("") }
        verify { mockSharedPrefManager.setUrlHost("") }
    }

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
        assertEquals("4025", ServerConfigUtils.getPinForUrl("192.168.1.73"))
        assertEquals("8925", ServerConfigUtils.getPinForUrl("192.168.1.66"))
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
