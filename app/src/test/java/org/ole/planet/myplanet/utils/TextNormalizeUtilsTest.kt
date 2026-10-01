package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizeUtilsTest {

    @Test
    fun `checkNA returns NA for null or empty string and returns original string otherwise`() {
        assertEquals("N/A", TextNormalizeUtils.checkNA(null))
        assertEquals("N/A", TextNormalizeUtils.checkNA(""))
        assertEquals("x", TextNormalizeUtils.checkNA("x"))
    }

    @Test
    fun `toHex converts string to hex representation and handles empty or null strings`() {
        assertEquals("68656c6c6f", TextNormalizeUtils.toHex("hello"))
        assertEquals("0", TextNormalizeUtils.toHex(""))
        assertEquals("", TextNormalizeUtils.toHex(null))
    }

    @Test
    fun `normalizeText normalizes text according to expected lowercasing and diacritics removal`() {
        assertEquals("hello world", TextNormalizeUtils.normalizeText("Hello WORLD"))
        assertEquals("cafe", TextNormalizeUtils.normalizeText("Café"))
        assertEquals("aeiou", TextNormalizeUtils.normalizeText("äëïöü"))
        assertEquals("مرحبا بك", TextNormalizeUtils.normalizeText("مرحبا بك"))
    }
}
