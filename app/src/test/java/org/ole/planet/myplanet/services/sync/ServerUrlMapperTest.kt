package org.ole.planet.myplanet.services.sync

import android.content.SharedPreferences
import android.net.Uri
import androidx.core.net.toUri
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.ole.planet.myplanet.utils.SecurePrefs

@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE, application=android.app.Application::class)
class ServerUrlMapperTest {

    private lateinit var serverUrlMapper: ServerUrlMapper

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SecurePrefs.warmUp(context)
        serverUrlMapper = ServerUrlMapper(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testProcessUrlWithKnownMapping() {
        val url = "http://${BuildConfig.PLANET_SANPABLO_URL}:80/db"

        val mapping = serverUrlMapper.processUrl(url)
        assertEquals(url, mapping.primaryUrl)
        assertEquals("http://${BuildConfig.PLANET_SANPABLO_URL}", mapping.extractedBaseUrl)
        assertEquals("https://${BuildConfig.PLANET_SANPABLO_CLONE_URL}", mapping.alternativeUrl)
    }

    @Test
    fun testProcessUrlWithUnknownMapping() {
        val url = "http://unknown.url:8080/db"

        val mapping = serverUrlMapper.processUrl(url)
        assertEquals(url, mapping.primaryUrl)
        assertEquals("http://unknown.url:8080", mapping.extractedBaseUrl)
        assertNull(mapping.alternativeUrl)
    }

    @Test
    fun testProcessUrlWithInvalidUrl() {
        val url = "invalid url"

        val mapping = serverUrlMapper.processUrl(url)
        assertEquals(url, mapping.primaryUrl)
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
        verify { editor.putString(eq("url_pwd"), match { it.startsWith("enc:") }) }
        verify { editor.putString("url_Scheme", "http") }
        verify { editor.putString("url_Host", "primary.com") }
        verify { editor.putString(eq("alternativeUrl"), match { it.startsWith("enc:") }) }
        verify { editor.putString(eq("processedAlternativeUrl"), match { it.startsWith("enc:") }) }
        verify { editor.putBoolean("isAlternativeUrl", true) }
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
        verify { editor.putString(eq("url_pwd"), match { it.startsWith("enc:") }) }
        verify { editor.putString("url_Scheme", "http") }
        verify { editor.putString("url_Host", "primary.com") }
        verify { editor.putString(eq("alternativeUrl"), match { it.startsWith("enc:") }) }
        verify { editor.putString(eq("processedAlternativeUrl"), match { it.startsWith("enc:") }) }
        verify { editor.putBoolean("isAlternativeUrl", true) }
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
        verify { editor.putString(eq("processedAlternativeUrl"), match { it.startsWith("enc:") }) }
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
}
