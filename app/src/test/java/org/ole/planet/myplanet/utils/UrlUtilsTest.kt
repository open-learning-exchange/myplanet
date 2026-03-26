package org.ole.planet.myplanet.utils

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.services.SharedPrefManager

class UrlUtilsTest {
    private lateinit var sharedPrefManager: SharedPrefManager

    @Before
    fun setup() {
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
        val mockSpm = mockk<SharedPrefManager>()
        every { mockSpm.isAlternativeUrl() } returns true
        every { mockSpm.getProcessedAlternativeUrl() } returns "http://192.168.1.2:5000"
        val result = UrlUtils.getApkVersionUrl(mockSpm)
        assertEquals("http://192.168.1.2:5000/apkversion", result)
    }

    @Test
    fun `getApkVersionUrl removes suffix db and appends apkversion`() {
        val mockSpm = mockk<SharedPrefManager>()
        every { mockSpm.isAlternativeUrl() } returns false
        every { mockSpm.getCouchdbUrl() } returns "http://192.168.1.1:5000/db"
        val result = UrlUtils.getApkVersionUrl(mockSpm)
        assertEquals("http://192.168.1.1:5000/apkversion", result)
    }

    @Test
    fun `getApkVersionUrl appends apkversion`() {
        val mockSpm = mockk<SharedPrefManager>()
        every { mockSpm.isAlternativeUrl() } returns false
        every { mockSpm.getCouchdbUrl() } returns "http://192.168.1.1:5000"
        val result = UrlUtils.getApkVersionUrl(mockSpm)
        assertEquals("http://192.168.1.1:5000/apkversion", result)
    }

    @Test
    fun `getApkVersionUrl with empty couchdb url returns apkversion`() {
        val mockSpm = mockk<SharedPrefManager>()
        every { mockSpm.isAlternativeUrl() } returns false
        every { mockSpm.getCouchdbUrl() } returns ""
        val result = UrlUtils.getApkVersionUrl(mockSpm)
        assertEquals("/apkversion", result)
    }

    @Test
    fun `getApkVersionUrl with alternative url empty returns apkversion`() {
        val mockSpm = mockk<SharedPrefManager>()
        every { mockSpm.isAlternativeUrl() } returns true
        every { mockSpm.getProcessedAlternativeUrl() } returns ""
        val result = UrlUtils.getApkVersionUrl(mockSpm)
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
