package org.ole.planet.myplanet.utils

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UrlUtilsTest {

    @Before
    fun setup() {
        mockkObject(UrlUtils)
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
}
