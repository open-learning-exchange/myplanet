package org.ole.planet.myplanet.utils

import android.app.Application
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.services.SharedPrefManager
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UrlUtilsTest {
    private lateinit var mockSpm: SharedPrefManager
    private lateinit var sharedPrefManager: SharedPrefManager

    @Before
    fun setUp() {
        mockSpm = mockk(relaxed = true)
        UrlUtils.resetForTesting()
        UrlUtils.init(mockSpm)

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

    @Test
    fun testBasicAuthHeader_withPaddingAndNoPadding() {
        // "user:pass" -> "dXNlcjpwYXNz" (no padding needed)
        val result1 = UrlUtils.basicAuthHeader("user", "pass")
        assertEquals("Basic dXNlcjpwYXNz", result1)

        // "user:password" -> "dXNlcjpwYXNzd29yZA==" (with '=' padding)
        val result2 = UrlUtils.basicAuthHeader("user", "password")
        assertEquals("Basic dXNlcjpwYXNzd29yZA==", result2)
    }

    @Test
    fun testGetUserInfo_nullInput() {
        val (user, pass) = UrlUtils.getUserInfo(null)
        assertEquals("", user)
        assertEquals("", pass)
    }

    @Test
    fun testGetUserInfo_emptyInput() {
        val (user, pass) = UrlUtils.getUserInfo("")
        assertEquals("", user)
        assertEquals("", pass)
    }

    @Test
    fun testGetUserInfo_validUsernameAndPassword() {
        val (user, pass) = UrlUtils.getUserInfo("admin:secret")
        assertEquals("admin", user)
        assertEquals("secret", pass)
    }

    @Test
    fun testGetUserInfo_noColon() {
        val (user, pass) = UrlUtils.getUserInfo("admin")
        assertEquals("", user)
        assertEquals("", pass)
    }

    @Test
    fun testGetUserInfo_colonWithoutPassword() {
        val (user, pass) = UrlUtils.getUserInfo("admin:")
        assertEquals("", user)
        assertEquals("", pass)
    }

    @Test
    fun testGetUserInfo_multipleColons() {
        val (user, pass) = UrlUtils.getUserInfo("admin:secret:extra")
        assertEquals("admin", user)
        assertEquals("secret", pass)
    }

    @Test
    fun `header memoizes basic auth header and invalidates when requested`() {
        every { mockSpm.getUrlUser() } returns "user1"
        every { mockSpm.getUrlPwd() } returns "pass1"

        val firstHeader = UrlUtils.header
        val secondHeader = UrlUtils.header

        assertEquals(firstHeader, secondHeader)
        verify(exactly = 1) { mockSpm.getUrlUser() }
        verify(exactly = 1) { mockSpm.getUrlPwd() }

        every { mockSpm.getUrlUser() } returns "user2"
        every { mockSpm.getUrlPwd() } returns "pass2"

        UrlUtils.invalidateHeaderCache()
        val thirdHeader = UrlUtils.header

        verify(exactly = 2) { mockSpm.getUrlUser() }
        verify(exactly = 2) { mockSpm.getUrlPwd() }
        assertNotEquals(firstHeader, thirdHeader)
    }

    @Test
    fun `resetForTesting clears cached header`() {
        every { mockSpm.getUrlUser() } returns "user1"
        every { mockSpm.getUrlPwd() } returns "pass1"

        val firstHeader = UrlUtils.header

        UrlUtils.resetForTesting()
        UrlUtils.init(mockSpm)

        val secondHeader = UrlUtils.header

        assertEquals(firstHeader, secondHeader)
        verify(exactly = 2) { mockSpm.getUrlUser() }
    }

    fun `getUrl with explicit base builds resource url without re-deriving base`() {
        unmockkObject(UrlUtils)
        val result = UrlUtils.getUrl("r1", "f1", "http://example.com/db")
        assertEquals("http://example.com/db/resources/r1/f1", result)
    }

    @Test
    fun `getUrl with explicit base resolves base once for many libraries`() {
        unmockkObject(UrlUtils)
        val spm = mockk<SharedPrefManager>(relaxed = true)
        every { spm.isAlternativeUrl() } returns false
        every { spm.getCouchdbUrl() } returns "http://example.com"
        UrlUtils.resetForTesting()
        UrlUtils.init(spm)

        val base = UrlUtils.getUrl()
        val urls = listOf("r1" to "f1", "r2" to "f2").map { (id, file) ->
            UrlUtils.getUrl(id, file, base)
        }

        assertEquals("http://example.com/db/resources/r1/f1", urls[0])
        assertEquals("http://example.com/db/resources/r2/f2", urls[1])
        verify(exactly = 1) { spm.getCouchdbUrl() }
    }
}
