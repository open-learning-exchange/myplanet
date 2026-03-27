package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.BuildConfig

class ServerConfigUtilsTest {

    @Test
    fun getPinForUrl_returnsCorrectPinForKnownUrls() {
        assertEquals(BuildConfig.PLANET_LEARNING_PIN, ServerConfigUtils.getPinForUrl(BuildConfig.PLANET_LEARNING_URL))
        assertEquals(BuildConfig.PLANET_GUATEMALA_PIN, ServerConfigUtils.getPinForUrl(BuildConfig.PLANET_GUATEMALA_URL))
        assertEquals(BuildConfig.PLANET_SANPABLO_PIN, ServerConfigUtils.getPinForUrl(BuildConfig.PLANET_SANPABLO_URL))
        assertEquals(BuildConfig.PLANET_EARTH_PIN, ServerConfigUtils.getPinForUrl(BuildConfig.PLANET_EARTH_URL))
        assertEquals(BuildConfig.PLANET_SOMALIA_PIN, ServerConfigUtils.getPinForUrl(BuildConfig.PLANET_SOMALIA_URL))
        assertEquals(BuildConfig.PLANET_VI_PIN, ServerConfigUtils.getPinForUrl(BuildConfig.PLANET_VI_URL))
        assertEquals(BuildConfig.PLANET_XELA_PIN, ServerConfigUtils.getPinForUrl(BuildConfig.PLANET_XELA_URL))
        assertEquals(BuildConfig.PLANET_URIUR_PIN, ServerConfigUtils.getPinForUrl(BuildConfig.PLANET_URIUR_URL))
        assertEquals(BuildConfig.PLANET_RUIRU_PIN, ServerConfigUtils.getPinForUrl(BuildConfig.PLANET_RUIRU_URL))
        assertEquals(BuildConfig.PLANET_EMBAKASI_PIN, ServerConfigUtils.getPinForUrl(BuildConfig.PLANET_EMBAKASI_URL))
        assertEquals(BuildConfig.PLANET_CAMBRIDGE_PIN, ServerConfigUtils.getPinForUrl(BuildConfig.PLANET_CAMBRIDGE_URL))
    }

    @Test
    fun getPinForUrl_returnsEmptyStringForUnknownUrl() {
        assertEquals("", ServerConfigUtils.getPinForUrl("https://unknown.url"))
    }

    @Test
    fun getPinForUrl_returnsEmptyStringForEmptyUrl() {
        assertEquals("", ServerConfigUtils.getPinForUrl(""))
    }
}
