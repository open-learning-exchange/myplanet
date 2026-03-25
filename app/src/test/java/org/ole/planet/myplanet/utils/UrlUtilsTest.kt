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
        val mockBaseUrl = "http://mockurl.com/db"
        every { UrlUtils.getUrl() } returns mockBaseUrl

        val userId: String? = null
        val imageName = "profile.jpg"

        val result = UrlUtils.getUserImageUrl(userId, imageName)

        assertEquals("$mockBaseUrl/_users/null/$imageName", result)
    }

    @Test
    fun testGetUserImageUrlWithEmptyStrings() {
        val mockBaseUrl = "http://mockurl.com/db"
        every { UrlUtils.getUrl() } returns mockBaseUrl

        val userId = ""
        val imageName = ""

        val result = UrlUtils.getUserImageUrl(userId, imageName)

        assertEquals("$mockBaseUrl/_users//", result)
    }

    @Test
    fun testGetUserImageUrlWithSpecialCharacters() {
        val mockBaseUrl = "http://mockurl.com/db"
        every { UrlUtils.getUrl() } returns mockBaseUrl

        val userId = "user@123"
        val imageName = "my image (1).jpg"

        val result = UrlUtils.getUserImageUrl(userId, imageName)

        assertEquals("$mockBaseUrl/_users/$userId/$imageName", result)
    }
}
