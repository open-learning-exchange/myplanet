package org.ole.planet.myplanet.services.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.BuildConfig
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class ServerUrlMapperTest {

    private lateinit var serverUrlMapper: ServerUrlMapper

    @Before
    fun setUp() {
        serverUrlMapper = ServerUrlMapper()
    }

    @Test
    fun processUrl_withMappedUrl_returnsCorrectMapping() {
        // Since we cannot easily change BuildConfig in tests, we use the values from gradle.properties
        // Example: PLANET_SANPABLO_URL=192.168.48.253, PLANET_SANPABLO_CLONE_URL=sanpablo.planet.gt

        val primaryUrl = "http://${BuildConfig.PLANET_SANPABLO_URL}/some/path"
        val expectedBaseUrl = "http://${BuildConfig.PLANET_SANPABLO_URL}"
        val expectedAlternativeUrl = "https://${BuildConfig.PLANET_SANPABLO_CLONE_URL}"

        val result = serverUrlMapper.processUrl(primaryUrl)

        assertEquals(primaryUrl, result.primaryUrl)
        assertEquals(expectedAlternativeUrl, result.alternativeUrl)
        assertEquals(expectedBaseUrl, result.extractedBaseUrl)
    }

    @Test
    fun processUrl_withUnmappedUrl_returnsNullAlternative() {
        val primaryUrl = "http://example.com/api"
        val expectedBaseUrl = "http://example.com"

        val result = serverUrlMapper.processUrl(primaryUrl)

        assertEquals(primaryUrl, result.primaryUrl)
        assertNull(result.alternativeUrl)
        assertEquals(expectedBaseUrl, result.extractedBaseUrl)
    }

    @Test
    fun processUrl_withInvalidUrl_returnsNullExtracted() {
        val primaryUrl = "not-a-url"

        val result = serverUrlMapper.processUrl(primaryUrl)

        assertEquals(primaryUrl, result.primaryUrl)
        assertNull(result.alternativeUrl)
        assertNull(result.extractedBaseUrl)
    }

    @Test
    fun processUrl_withDifferentProtocol_returnsNullAlternative() {
        // Mapping is specifically for http
        val primaryUrl = "https://${BuildConfig.PLANET_SANPABLO_URL}/some/path"
        val expectedBaseUrl = "https://${BuildConfig.PLANET_SANPABLO_URL}"

        val result = serverUrlMapper.processUrl(primaryUrl)

        assertEquals(primaryUrl, result.primaryUrl)
        assertNull(result.alternativeUrl)
        assertEquals(expectedBaseUrl, result.extractedBaseUrl)
    }

    @Test
    fun processUrl_withDifferentPort_returnsNullAlternative() {
        val primaryUrl = "http://${BuildConfig.PLANET_SANPABLO_URL}:8080/some/path"
        val expectedBaseUrl = "http://${BuildConfig.PLANET_SANPABLO_URL}:8080"

        val result = serverUrlMapper.processUrl(primaryUrl)

        assertEquals(primaryUrl, result.primaryUrl)
        assertNull(result.alternativeUrl)
        assertEquals(expectedBaseUrl, result.extractedBaseUrl)
    }
}
