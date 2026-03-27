package org.ole.planet.myplanet.utils

import androidx.test.core.app.ApplicationProvider
import io.mockk.every
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

        verify { mockSharedPrefManager.setServerPin(password) }
        verify { mockSharedPrefManager.setUrlUser("admin") }
        verify { mockSharedPrefManager.setUrlPwd("admin123") }
        verify { mockSharedPrefManager.setUrlScheme("http") }
        verify { mockSharedPrefManager.setUrlHost("demo.ole.org") }
        verify { mockSharedPrefManager.setAlternativeUrl(url) }
        verify { mockSharedPrefManager.setProcessedAlternativeUrl(url) }
        verify { mockSharedPrefManager.setIsAlternativeUrl(true) }
    }
}
