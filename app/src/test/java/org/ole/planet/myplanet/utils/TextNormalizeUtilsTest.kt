package org.ole.planet.myplanet.utils

import org.junit.Assert.assertArrayEquals
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
    fun `toHex returns hex representation for valid strings and empty string for null`() {
        assertEquals("68656c6c6f", TextNormalizeUtils.toHex("hello"))
        assertEquals("0", TextNormalizeUtils.toHex(""))
        assertEquals("", TextNormalizeUtils.toHex(null))
    }

    @Test
    fun `normalizeText fast path handles pure ASCII inputs correctly`() {
        // Fast path: mixed case, empty string, digits and punctuation, plain ASCII words
        assertEquals("hello world", TextNormalizeUtils.normalizeText("Hello WORLD"))
        assertEquals("", TextNormalizeUtils.normalizeText(""))
        assertEquals("123!@# $%-=", TextNormalizeUtils.normalizeText("123!@# $%-="))
        assertEquals("simple test", TextNormalizeUtils.normalizeText("simple test"))
    }

    @Test
    fun `normalizeText slow path produces byte-identical output to original NFD normalization`() {
        // Helper function representing the original implementation
        fun legacyNormalizeText(str: String): String {
            val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
            return java.text.Normalizer.normalize(str.lowercase(java.util.Locale.getDefault()), java.text.Normalizer.Form.NFD)
                .replace(DIACRITICS_REGEX, "")
        }

        val testCases = listOf(
            "Café",
            "Niño",
            "áéíóú",
            "مرحبا بك",
            "नमस्ते"
        )

        for (input in testCases) {
            val expected = legacyNormalizeText(input)
            val actual = TextNormalizeUtils.normalizeText(input)
            assertEquals(expected, actual)
            assertArrayEquals(
                "Byte array mismatch for input: $input",
                expected.toByteArray(Charsets.UTF_8),
                actual.toByteArray(Charsets.UTF_8)
            )
        }

        // Specific expected value assertions as requested
        assertEquals("cafe", TextNormalizeUtils.normalizeText("Café"))
        assertEquals("nino", TextNormalizeUtils.normalizeText("Niño"))
        assertEquals("aeiou", TextNormalizeUtils.normalizeText("áéíóú"))
    }
}
