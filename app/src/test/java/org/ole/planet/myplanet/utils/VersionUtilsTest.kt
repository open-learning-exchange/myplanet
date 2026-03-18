package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionUtilsTest {

    @Test
    fun compareVersions_should_return_0_for_equal_versions() {
        assertEquals(0, VersionUtils.compareVersions("1.0.0", "1.0.0"))
        assertEquals(0, VersionUtils.compareVersions("v1.0.0", "v1.0.0"))
        assertEquals(0, VersionUtils.compareVersions("1.0.0-lite", "1.0.0"))
    }

    @Test
    fun compareVersions_should_return_positive_for_newer_version() {
        assertTrue(VersionUtils.compareVersions("2.0.0", "1.0.0") > 0)
        assertTrue(VersionUtils.compareVersions("1.1.0", "1.0.0") > 0)
        assertTrue(VersionUtils.compareVersions("1.0.1", "1.0.0") > 0)
    }

    @Test
    fun compareVersions_should_return_negative_for_older_version() {
        assertTrue(VersionUtils.compareVersions("1.0.0", "2.0.0") < 0)
        assertTrue(VersionUtils.compareVersions("1.0.0", "1.1.0") < 0)
        assertTrue(VersionUtils.compareVersions("1.0.0", "1.0.1") < 0)
    }

    @Test
    fun compareVersions_should_handle_v_prefix_and_lite_suffix_correctly() {
        assertEquals(0, VersionUtils.compareVersions("v1.0.0-lite", "v1.0.0"))
        assertTrue(VersionUtils.compareVersions("v2.0.0-lite", "v1.0.0") > 0)
        assertTrue(VersionUtils.compareVersions("v1.0.0-lite", "v2.0.0") < 0)
    }

    @Test(expected = NumberFormatException::class)
    fun compareVersions_should_throw_on_malformed_string_inputs() {
        VersionUtils.compareVersions("abc", "1.0.0")
    }

    @Test
    fun compareVersions_should_not_throw_on_insufficient_version_parts() {
        // The implementation uses kotlin.math.min(parts1.size, parts2.size)
        // so it actually handles "1.0" vs "1.0.0" without IndexOutOfBoundsException
        // and returns a size comparison when the common prefix matches.
        assertTrue(VersionUtils.compareVersions("1.0", "1.0.0") < 0)
        assertTrue(VersionUtils.compareVersions("1.0.0", "1.0") > 0)
    }

    @Test
    fun isVersionAllowed_should_return_true_if_current_version_is_newer_or_equal() {
        assertTrue(VersionUtils.isVersionAllowed("1.0.0", "1.0.0"))
        assertTrue(VersionUtils.isVersionAllowed("2.0.0", "1.0.0"))
        assertTrue(VersionUtils.isVersionAllowed("v2.0.0-lite", "v1.0.0"))
    }

    @Test
    fun isVersionAllowed_should_return_false_if_current_version_is_older() {
        assertFalse(VersionUtils.isVersionAllowed("1.0.0", "2.0.0"))
        assertFalse(VersionUtils.isVersionAllowed("v1.0.0-lite", "v2.0.0"))
    }

    @Test
    fun parseApkVersionString_should_handle_valid_strings() {
        assertEquals(100, VersionUtils.parseApkVersionString("1.0.0"))
        assertEquals(100, VersionUtils.parseApkVersionString("v1.0.0"))
        assertEquals(123, VersionUtils.parseApkVersionString("1.2.3"))
        assertEquals(12, VersionUtils.parseApkVersionString("0.1.2"))
    }

    @Test
    fun parseApkVersionString_should_document_latent_multi_leading_zero_behavior() {
        // Exposes the fragility of stripping dots where 0.0.12 becomes 0012,
        // stripped to 012 -> returns 12. Same result as 0.0.2 -> 002 -> 02 -> 2.
        assertEquals(12, VersionUtils.parseApkVersionString("0.0.12"))
        assertEquals(2, VersionUtils.parseApkVersionString("0.0.2"))
    }

    @Test
    fun parseApkVersionString_should_return_null_for_empty_or_null_input() {
        assertNull(VersionUtils.parseApkVersionString(null))
        assertNull(VersionUtils.parseApkVersionString(""))
    }
}
