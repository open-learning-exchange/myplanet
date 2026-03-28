package org.ole.planet.myplanet.utils

import android.content.Context
import android.net.Uri
import dagger.hilt.android.EntryPointAccessors
import io.mockk.mockk
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.mockk
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.di.AutoSyncEntryPoint
import org.ole.planet.myplanet.services.SharedPrefManager
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class UrlUtilsTest {

    private lateinit var mockSpm: SharedPrefManager
    private lateinit var mockEntryPoint: AutoSyncEntryPoint
    private lateinit var sharedPrefManager: SharedPrefManager

    @Before
    fun setUp() {
        mockSpm = mockk(relaxed = true)
        mockEntryPoint = mockk()
        every { mockEntryPoint.sharedPrefManager() } returns mockSpm

        mockkStatic(EntryPointAccessors::class)
        every { EntryPointAccessors.fromApplication(any(), AutoSyncEntryPoint::class.java) } returns mockEntryPoint

        mockkObject(MainApplication.Companion)
        val mockContext = mockk<Context>()
        every { MainApplication.context } returns mockContext

        mockkObject(UrlUtils)
        sharedPrefManager = mockk(relaxed = true)
        every { sharedPrefManager.isAlternativeUrl() } returns false
        every { sharedPrefManager.getCouchdbUrl() } returns "http://example.com"
        every { sharedPrefManager.getProcessedAlternativeUrl() } returns "http://alternative.com"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `hostUrl fallback behavior when toUri throws Exception`() {
        every { mockSpm.getUrlScheme() } returns "http"
        every { mockSpm.getUrlHost() } returns "fallback.org"
        every { mockSpm.isAlternativeUrl() } returns true
        every { mockSpm.getProcessedAlternativeUrl() } returns "invalid://url"

        mockkStatic(Uri::class)
        every { Uri.parse("invalid://url") } throws RuntimeException("Simulated URI Exception")

        val result = UrlUtils.hostUrl

        assertEquals("http://fallback.org/ml/", result)
    }

    @Test
    fun `hostUrl successfully returns alternative URL`() {
        every { mockSpm.getUrlScheme() } returns "http"
        every { mockSpm.getUrlHost() } returns "fallback.org"
        every { mockSpm.isAlternativeUrl() } returns true
        every { mockSpm.getProcessedAlternativeUrl() } returns "https://newhost.com"

        val result = UrlUtils.hostUrl

        assertEquals("https://newhost.com:5000/", result)
    }

    @Test
    fun `hostUrl successfully returns standard URL`() {
        every { mockSpm.getUrlScheme() } returns "http"
        every { mockSpm.getUrlHost() } returns "standard.org"
        every { mockSpm.isAlternativeUrl() } returns false
        every { mockSpm.getProcessedAlternativeUrl() } returns ""

        val result = UrlUtils.hostUrl

        assertEquals("http://standard.org/ml/", result)
    }

    @Test
    fun `hostUrl successfully returns URL when alternativeUrl is true but value is empty`() {
        every { mockSpm.getUrlScheme() } returns "http"
        every { mockSpm.getUrlHost() } returns "fallback.org"
        every { mockSpm.isAlternativeUrl() } returns true
        every { mockSpm.getProcessedAlternativeUrl() } returns ""

        val result = UrlUtils.hostUrl

        assertEquals("http://fallback.org/ml/", result)
    }

    @Test
    fun testGetUserImageUrlReturnsCorrectFormattedString() {
        val mockBaseUrl = "http://mockurl.com/db"
        every { UrlUtils.getUrl() } returns mockBaseUrl

        val userId = "user123"
        val imageName = "profile.jpg"

        val result = UrlUtils.getUserImageUrl(userId, imageName)

        assertEquals("$mockBaseUrl/_users/$userId/$imageName", result)
    }

    @Test
    fun testGetUserImageUrlWithNullUserId() {
        val userId: String? = null
        val imageName = "profile.jpg"

        val result = UrlUtils.getUserImageUrl(userId, imageName)

        assertEquals(null, result)
    }

    @Test
    fun testGetUserImageUrlWithEmptyStrings() {
        val userId = ""
        val imageName = ""

        val result = UrlUtils.getUserImageUrl(userId, imageName)

        assertEquals(null, result)
    }

    @Test
    fun testGetUserImageUrlWithSpecialCharacters() {
        val mockBaseUrl = "http://mockurl.com/db"
        every { UrlUtils.getUrl() } returns mockBaseUrl

        val userId = "user@123"
        val imageName = "my image (1).jpg"

        val result = UrlUtils.getUserImageUrl(userId, imageName)

        assertEquals("$mockBaseUrl/_users/user%40123/my%20image%20%281%29.jpg", result)
    }

    @Test
    fun `getUpdateUrl should append versions to base url when not alternative`() {
        val spm = mockk<SharedPrefManager>()
        every { spm.isAlternativeUrl() } returns false
        every { spm.getCouchdbUrl() } returns "http://example.com"
        val result = UrlUtils.getUpdateUrl(spm)
        assertEquals("http://example.com/versions", result)
    }

    @Test
    fun `getUpdateUrl should append versions to base url when alternative`() {
        val spm = mockk<SharedPrefManager>()
        every { spm.isAlternativeUrl() } returns true
        every { spm.getProcessedAlternativeUrl() } returns "http://alt.example.com"
        val result = UrlUtils.getUpdateUrl(spm)
        assertEquals("http://alt.example.com/versions", result)
    }

    @Test
    fun `getUpdateUrl should remove trailing db before appending versions`() {
        val spm = mockk<SharedPrefManager>()
        every { spm.isAlternativeUrl() } returns false
        every { spm.getCouchdbUrl() } returns "http://example.com/db"
        val result = UrlUtils.getUpdateUrl(spm)
        assertEquals("http://example.com/versions", result)
    }

    @Test
    fun `getUpdateUrl should remove trailing db from alternative url before appending versions`() {
        val spm = mockk<SharedPrefManager>()
        every { spm.isAlternativeUrl() } returns true
        every { spm.getProcessedAlternativeUrl() } returns "http://alt.example.com/db"
        val result = UrlUtils.getUpdateUrl(spm)
        assertEquals("http://alt.example.com/versions", result)
    }

    @Test
    fun testGetHealthAccessUrl_withEmptyPin() {
        every { sharedPrefManager.getServerPin() } returns ""
        val url = UrlUtils.getHealthAccessUrl(sharedPrefManager)
        assertEquals("http://example.com/healthaccess?p=0000", url)
    }

    @Test
    fun testGetHealthAccessUrl_withCustomPin() {
        every { sharedPrefManager.getServerPin() } returns "1234"
        val url = UrlUtils.getHealthAccessUrl(sharedPrefManager)
        assertEquals("http://example.com/healthaccess?p=1234", url)
    }

    @Test
    fun testGetHealthAccessUrl_withAlternativeUrl() {
        every { sharedPrefManager.isAlternativeUrl() } returns true
        every { sharedPrefManager.getServerPin() } returns "5678"
        val url = UrlUtils.getHealthAccessUrl(sharedPrefManager)
        assertEquals("http://alternative.com/healthaccess?p=5678", url)
    }

    @Test
    fun testGetHealthAccessUrl_withDbSuffix() {
        every { sharedPrefManager.getCouchdbUrl() } returns "http://example.com/db"
        every { sharedPrefManager.getServerPin() } returns "4321"
        val url = UrlUtils.getHealthAccessUrl(sharedPrefManager)
        assertEquals("http://example.com/healthaccess?p=4321", url)
    }

    @Test
    fun `getApkVersionUrl uses alternative url`() {
        val spm = mockk<SharedPrefManager>()
        every { spm.isAlternativeUrl() } returns true
        every { spm.getProcessedAlternativeUrl() } returns "http://192.168.1.2:5000"
        val result = UrlUtils.getApkVersionUrl(spm)
        assertEquals("http://192.168.1.2:5000/apkversion", result)
    }

    @Test
    fun `getApkVersionUrl removes suffix db and appends apkversion`() {
        val spm = mockk<SharedPrefManager>()
        every { spm.isAlternativeUrl() } returns false
        every { spm.getCouchdbUrl() } returns "http://192.168.1.1:5000/db"
        val result = UrlUtils.getApkVersionUrl(spm)
        assertEquals("http://192.168.1.1:5000/apkversion", result)
    }

    @Test
    fun `getApkVersionUrl appends apkversion`() {
        val spm = mockk<SharedPrefManager>()
        every { spm.isAlternativeUrl() } returns false
        every { spm.getCouchdbUrl() } returns "http://192.168.1.1:5000"
        val result = UrlUtils.getApkVersionUrl(spm)
        assertEquals("http://192.168.1.1:5000/apkversion", result)
    }

    @Test
    fun `getApkVersionUrl with empty couchdb url returns apkversion`() {
        val spm = mockk<SharedPrefManager>()
        every { spm.isAlternativeUrl() } returns false
        every { spm.getCouchdbUrl() } returns ""
        val result = UrlUtils.getApkVersionUrl(spm)
        assertEquals("/apkversion", result)
    }

    @Test
    fun `getApkVersionUrl with alternative url empty returns apkversion`() {
        val spm = mockk<SharedPrefManager>()
        every { spm.isAlternativeUrl() } returns true
        every { spm.getProcessedAlternativeUrl() } returns ""
        val result = UrlUtils.getApkVersionUrl(spm)
        assertEquals("/apkversion", result)
    }

    @Test
    fun testDbUrl_withDbSuffix() {
        val input = "http://example.com/db"
        val expected = "http://example.com/db"
        val result = UrlUtils.dbUrl(input)
        assertEquals(expected, result)
    }

    @Test
    fun testDbUrl_withoutDbSuffix() {
        val input = "http://example.com"
        val expected = "http://example.com/db"
        val result = UrlUtils.dbUrl(input)
        assertEquals(expected, result)
    }

    @Test
    fun testDbUrl_withTrailingSlash() {
        val input = "http://example.com/"
        val expected = "http://example.com/db"
        val result = UrlUtils.dbUrl(input)
        assertEquals(expected, result)
    }

    @Test
    fun testDbUrl_withSharedPrefManager_alternativeUrl() {
        val spm = mockk<SharedPrefManager>()
        every { spm.isAlternativeUrl() } returns true
        every { spm.getProcessedAlternativeUrl() } returns "http://alt.example.com"
        val expected = "http://alt.example.com/db"
        val result = UrlUtils.dbUrl(spm)
        assertEquals(expected, result)
    }

    @Test
    fun testDbUrl_withSharedPrefManager_couchdbUrl() {
        val spm = mockk<SharedPrefManager>()
        every { spm.isAlternativeUrl() } returns false
        every { spm.getCouchdbUrl() } returns "http://couch.example.com"
        val expected = "http://couch.example.com/db"
        val result = UrlUtils.dbUrl(spm)
        assertEquals(expected, result)
    }
}
