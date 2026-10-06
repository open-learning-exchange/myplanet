package org.ole.planet.myplanet.services.sync

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.BuildConfig
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.FakeKeyValueStore
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.fakeSharedPrefManager
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

    private fun mockUri(userInfo: String? = null): Uri {
        val uri = mockk<Uri>()
        every { uri.userInfo } returns userInfo
        every { uri.scheme } returns "http"
        every { uri.host } returns "primary.com"
        return uri
    }

    @Test
    fun testUpdateUrlPreferencesWithUserInfo() {
        val store = FakeKeyValueStore()
        val alternativeUrl = "http://user:pass@alternative.com:5984"
        val url = "http://primary.com"

        serverUrlMapper.updateUrlPreferences(fakeSharedPrefManager(store), mockUri("user:pass"), alternativeUrl, url)

        assertEquals(1, store.editCount)
        assertEquals(
            mapOf<String, Any>(
                "url_user" to "user",
                "url_pwd" to "pass",
                "url_Scheme" to "http",
                "url_Host" to "primary.com",
                "alternativeUrl" to url,
                "processedAlternativeUrl" to alternativeUrl,
                "isAlternativeUrl" to true
            ),
            store.values
        )
    }

    @Test
    fun testUpdateUrlPreferencesExtractsCredentialsFromAlternativeUrlNotPrimary() {
        val store = FakeKeyValueStore()
        val alternativeUrl = "http://clone_user:clone_pass@alternative.com:5984"

        serverUrlMapper.updateUrlPreferences(fakeSharedPrefManager(store), mockUri(), alternativeUrl, "http://primary.com")

        assertEquals("clone_user", store.values["url_user"])
        assertEquals("clone_pass", store.values["url_pwd"])
        assertEquals("http", store.values["url_Scheme"])
        assertEquals("primary.com", store.values["url_Host"])
        assertEquals(alternativeUrl, store.values["processedAlternativeUrl"])
    }

    @Test
    fun testUpdateUrlPreferencesWithoutUserInfo() {
        val store = FakeKeyValueStore(mapOf("serverPin" to "1234"))
        val url = "http://primary.com"

        serverUrlMapper.updateUrlPreferences(fakeSharedPrefManager(store), mockUri(), "https://alternative.com", url)

        assertEquals(
            mapOf<String, Any>(
                "serverPin" to "1234",
                "url_user" to "satellite",
                "url_pwd" to "1234",
                "url_Scheme" to "http",
                "url_Host" to "primary.com",
                "alternativeUrl" to url,
                "processedAlternativeUrl" to "https://satellite:1234@alternative.com:443",
                "isAlternativeUrl" to true
            ),
            store.values
        )
    }

    @Test
    fun testUpdateUrlPreferencesReusesParsedUserInfoWhenPasswordContainsAtSign() {
        val store = FakeKeyValueStore()
        val alternativeUrl = "http://user:p@ss@alternative.com:5984"

        serverUrlMapper.updateUrlPreferences(fakeSharedPrefManager(store), mockUri(), alternativeUrl, "http://primary.com")

        assertEquals("user", store.values["url_user"])
        assertEquals("p@ss", store.values["url_pwd"])
        assertEquals(alternativeUrl, store.values["processedAlternativeUrl"])
    }

    @Test
    fun testUpdateServerIfNecessaryWhenPrimaryIsDownAndAlternativeIsUp() = runTest {
        val store = FakeKeyValueStore(mapOf("serverPin" to "1234"))

        val mapping = ServerUrlMapper.UrlMapping(
            primaryUrl = "http://primary.com",
            alternativeUrl = "https://alternative.com",
            extractedBaseUrl = "http://primary.com"
        )

        val isServerReachable: suspend (String) -> Boolean = { url ->
            url == "https://alternative.com"
        }

        serverUrlMapper.updateServerIfNecessary(mapping, fakeSharedPrefManager(store), isServerReachable)

        assertEquals(1, store.editCount)
        assertEquals("https://satellite:1234@alternative.com:443", store.values["processedAlternativeUrl"])
    }

    @Test
    fun testUpdateServerIfNecessaryWhenPrimaryIsUp() = runTest {
        val store = FakeKeyValueStore()

        val mapping = ServerUrlMapper.UrlMapping(
            primaryUrl = "http://primary.com",
            alternativeUrl = "https://alternative.com",
            extractedBaseUrl = "http://primary.com"
        )

        val probedUrls = mutableListOf<String>()
        val isServerReachable: suspend (String) -> Boolean = { url ->
            probedUrls.add(url)
            true
        }

        serverUrlMapper.updateServerIfNecessary(mapping, fakeSharedPrefManager(store), isServerReachable)

        assertEquals(listOf("http://primary.com"), probedUrls)
        assertEquals(0, store.editCount)
        assertFalse(store.contains("processedAlternativeUrl"))
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

        val settings = fakeSharedPrefManager(FakeKeyValueStore(mapOf("serverPin" to "1234")))
        val uri = mockk<Uri>(relaxed = true)
        every { uri.scheme } returns "http"
        every { uri.host } returns "primary.com"

        serverUrlMapper.updateUrlPreferences(settings, uri, "https://alternative.com", "http://primary.com")

        val secondHeader = UrlUtils.header
        assertEquals("Basic " + android.util.Base64.encodeToString("satellite:1234".toByteArray(), android.util.Base64.NO_WRAP), secondHeader)
        verify(exactly = 2) { spm.getUrlUser() }
    }
}
