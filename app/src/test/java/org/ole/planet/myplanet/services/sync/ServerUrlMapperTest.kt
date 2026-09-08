package org.ole.planet.myplanet.services.sync

import android.content.SharedPreferences
import android.net.Uri
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.BuildConfig
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE, application=android.app.Application::class)
class ServerUrlMapperTest {

    private lateinit var serverUrlMapper: ServerUrlMapper
    private val dispatcherProvider: DispatcherProvider = mockk(relaxed = true)

    @Before
    fun setUp() {
        serverUrlMapper = ServerUrlMapper(dispatcherProvider)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testProcessUrlMappedPrimaryToCorrectAlternative() {
        val url = "http://${BuildConfig.PLANET_SANPABLO_URL}:80/db"

        val mapping = serverUrlMapper.processUrl(url)
        assertEquals(url, mapping.primaryUrl)
        assertEquals("http://${BuildConfig.PLANET_SANPABLO_URL}", mapping.extractedBaseUrl)
        assertEquals("https://${BuildConfig.PLANET_SANPABLO_CLONE_URL}", mapping.alternativeUrl)
    }

    @Test
    fun testProcessUrlUnmappedHostReturnsNullAlternativeUrl() {
        val url = "http://unmapped.host.com/db"

        val mapping = serverUrlMapper.processUrl(url)
        assertEquals(url, mapping.primaryUrl)
        assertEquals("http://unmapped.host.com", mapping.extractedBaseUrl)
        assertNull(mapping.alternativeUrl)
    }

    @Test
    fun testProcessUrlPreservesNonDefaultPortInExtractedBaseUrl() {
        val url = "http://unmapped.host.com:8080/db"

        val mapping = serverUrlMapper.processUrl(url)
        assertEquals(url, mapping.primaryUrl)
        assertEquals("http://unmapped.host.com:8080", mapping.extractedBaseUrl)
        assertNull(mapping.alternativeUrl)
    }

    @Test
    fun testProcessUrlMalformedStringReturnsNullWithoutThrowing() {
        val malformedUrl = "invalid url"

        val mapping = serverUrlMapper.processUrl(malformedUrl)
        assertEquals(malformedUrl, mapping.primaryUrl)
        assertNull(mapping.extractedBaseUrl)
        assertNull(mapping.alternativeUrl)
    }

    @Test
    fun testUpdateUrlPreferencesWithUserInfo() {
        val editor = mockk<SharedPreferences.Editor>()
        val settings = mockk<SharedPreferences>()

        every { editor.putString(any(), any()) } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.apply() } just Runs

        val uri = mockk<Uri>()
        every { uri.userInfo } returns "user:pass"
        every { uri.scheme } returns "http"
        every { uri.host } returns "primary.com"

        val alternativeUrl = "http://user:pass@alternative.com:5984"

        val url = "http://primary.com"

        serverUrlMapper.updateUrlPreferences(editor, uri, alternativeUrl, url, settings)

        verify { editor.putString("url_user", "user") }
        verify { editor.putString("url_pwd", "pass") }
        verify { editor.putString("url_Scheme", "http") }
        verify { editor.putString("url_Host", "primary.com") }
        verify { editor.putString("alternativeUrl", url) }
        verify { editor.putString("processedAlternativeUrl", alternativeUrl) }
        verify { editor.putBoolean("isAlternativeUrl", true) }
        verify { editor.apply() }
    }

    @Test
    fun testUpdateUrlPreferencesExtractsCredentialsFromAlternativeUrlNotPrimary() {
        val editor = mockk<SharedPreferences.Editor>()
        val settings = mockk<SharedPreferences>()

        every { editor.putString(any(), any()) } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.apply() } just Runs

        val uri = mockk<Uri>()
        every { uri.userInfo } returns null
        every { uri.scheme } returns "http"
        every { uri.host } returns "primary.com"

        val alternativeUrl = "http://clone_user:clone_pass@alternative.com:5984"

        val url = "http://primary.com"

        serverUrlMapper.updateUrlPreferences(editor, uri, alternativeUrl, url, settings)

        verify { editor.putString("url_user", "clone_user") }
        verify { editor.putString("url_pwd", "clone_pass") }
        verify { editor.putString("url_Scheme", "http") }
        verify { editor.putString("url_Host", "primary.com") }
        verify { editor.putString("processedAlternativeUrl", alternativeUrl) }
        verify { editor.apply() }
    }

    @Test
    fun testUpdateUrlPreferencesWithoutUserInfo() {
        val editor = mockk<SharedPreferences.Editor>()
        val settings = mockk<SharedPreferences>()

        every { editor.putString(any(), any()) } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.apply() } just Runs
        every { settings.getString("serverPin", "") } returns "1234"

        val uri = mockk<Uri>()
        every { uri.userInfo } returns null
        every { uri.scheme } returns "http"
        every { uri.host } returns "primary.com"

        val alternativeUrl = "https://alternative.com"

        val url = "http://primary.com"

        serverUrlMapper.updateUrlPreferences(editor, uri, alternativeUrl, url, settings)

        verify { editor.putString("url_user", "satellite") }
        verify { editor.putString("url_pwd", "1234") }
        verify { editor.putString("url_Scheme", "http") }
        verify { editor.putString("url_Host", "primary.com") }
        verify { editor.putString("alternativeUrl", url) }
        verify { editor.putString("processedAlternativeUrl", "https://satellite:1234@alternative.com:443") }
        verify { editor.putBoolean("isAlternativeUrl", true) }
        verify { editor.apply() }
    }

    @Test
    fun testUpdateUrlPreferencesReusesParsedUserInfoWhenPasswordContainsAtSign() {
        val editor = mockk<SharedPreferences.Editor>()
        val settings = mockk<SharedPreferences>()

        every { editor.putString(any(), any()) } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.apply() } just Runs

        val uri = mockk<Uri>()
        every { uri.userInfo } returns null
        every { uri.scheme } returns "http"
        every { uri.host } returns "primary.com"

        val alternativeUrl = "http://user:p@ss@alternative.com:5984"

        val url = "http://primary.com"

        serverUrlMapper.updateUrlPreferences(editor, uri, alternativeUrl, url, settings)

        verify { editor.putString("url_user", "user") }
        verify { editor.putString("url_pwd", "p@ss") }
        verify { editor.putString("processedAlternativeUrl", alternativeUrl) }
        verify { editor.apply() }
    }

    @Test
    fun testUpdateServerIfNecessaryWhenPrimaryIsDownAndAlternativeIsUp() = runTest {
        val editor = mockk<SharedPreferences.Editor>()
        val settings = mockk<SharedPreferences>()

        every { settings.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.apply() } just Runs
        every { settings.getString("serverPin", "") } returns "1234"

        val mapping = ServerUrlMapper.UrlMapping(
            primaryUrl = "http://primary.com",
            alternativeUrl = "https://alternative.com",
            extractedBaseUrl = "http://primary.com"
        )


        val isServerReachable: suspend (String) -> Boolean = { url ->
            url == "https://alternative.com"
        }

        serverUrlMapper.updateServerIfNecessary(mapping, settings, isServerReachable)

        verify { settings.edit() }
        verify { editor.putString("processedAlternativeUrl", "https://satellite:1234@alternative.com:443") }
    }

    @Test
    fun testUpdateServerIfNecessaryWhenPrimaryIsUp() = runTest {
        val settings = mockk<SharedPreferences>()

        val mapping = ServerUrlMapper.UrlMapping(
            primaryUrl = "http://primary.com",
            alternativeUrl = "https://alternative.com",
            extractedBaseUrl = "http://primary.com"
        )

        val isServerReachable: suspend (String) -> Boolean = { true }

        serverUrlMapper.updateServerIfNecessary(mapping, settings, isServerReachable)

        verify(exactly = 0) { settings.edit() }
    }

    @Test
    fun `updateUrlPreferences invalidates UrlUtils cached header`() {
        val spm = mockk<SharedPrefManager>(relaxed = true)
        UrlUtils.resetForTesting()
        UrlUtils.init(spm)

        every { spm.getUrlUser() } returns "oldUser"
        every { spm.getUrlPwd() } returns "oldPwd"

        val firstHeader = UrlUtils.header
        assertEquals("Basic " + android.util.Base64.encodeToString("oldUser:oldPwd".toByteArray(), android.util.Base64.NO_WRAP), firstHeader)

        every { spm.getUrlUser() } returns "satellite"
        every { spm.getUrlPwd() } returns "1234"

        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val settings = mockk<SharedPreferences>(relaxed = true)
        every { settings.getString("serverPin", "") } returns "1234"

        val uri = mockk<Uri>(relaxed = true)
        every { uri.scheme } returns "http"
        every { uri.host } returns "primary.com"

        serverUrlMapper.updateUrlPreferences(editor, uri, "https://alternative.com", "http://primary.com", settings)

        val secondHeader = UrlUtils.header
        assertEquals("Basic " + android.util.Base64.encodeToString("satellite:1234".toByteArray(), android.util.Base64.NO_WRAP), secondHeader)
        verify(exactly = 2) { spm.getUrlUser() }
    }
}
