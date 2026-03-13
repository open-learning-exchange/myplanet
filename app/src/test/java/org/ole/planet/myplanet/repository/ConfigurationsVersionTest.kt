package org.ole.planet.myplanet.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigurationsVersionTest {

    @Test
    fun compareVersions_should_return_0_for_equal_versions() {
        assertEquals(0, ConfigurationsRepositoryImpl.compareVersions("1.0.0", "1.0.0"))
        assertEquals(0, ConfigurationsRepositoryImpl.compareVersions("v1.0.0", "v1.0.0"))
        assertEquals(0, ConfigurationsRepositoryImpl.compareVersions("1.0.0-lite", "1.0.0"))
    }

    @Test
    fun compareVersions_should_return_positive_for_newer_version() {
        assertTrue(ConfigurationsRepositoryImpl.compareVersions("2.0.0", "1.0.0") > 0)
        assertTrue(ConfigurationsRepositoryImpl.compareVersions("1.1.0", "1.0.0") > 0)
        assertTrue(ConfigurationsRepositoryImpl.compareVersions("1.0.1", "1.0.0") > 0)
    }

    @Test
    fun compareVersions_should_return_negative_for_older_version() {
        assertTrue(ConfigurationsRepositoryImpl.compareVersions("1.0.0", "2.0.0") < 0)
        assertTrue(ConfigurationsRepositoryImpl.compareVersions("1.0.0", "1.1.0") < 0)
        assertTrue(ConfigurationsRepositoryImpl.compareVersions("1.0.0", "1.0.1") < 0)
    }

    @Test
    fun compareVersions_should_handle_v_prefix_and_lite_suffix_correctly() {
        assertEquals(0, ConfigurationsRepositoryImpl.compareVersions("v1.0.0-lite", "v1.0.0"))
        assertTrue(ConfigurationsRepositoryImpl.compareVersions("v2.0.0-lite", "v1.0.0") > 0)
        assertTrue(ConfigurationsRepositoryImpl.compareVersions("v1.0.0-lite", "v2.0.0") < 0)
    }

    @Test
    fun isVersionAllowed_should_return_true_if_current_version_is_newer_or_equal() {
        assertTrue(ConfigurationsRepositoryImpl.isVersionAllowed("1.0.0", "1.0.0"))
        assertTrue(ConfigurationsRepositoryImpl.isVersionAllowed("2.0.0", "1.0.0"))
        assertTrue(ConfigurationsRepositoryImpl.isVersionAllowed("v2.0.0-lite", "v1.0.0"))
    }

    @Test
    fun isVersionAllowed_should_return_false_if_current_version_is_older() {
        assertFalse(ConfigurationsRepositoryImpl.isVersionAllowed("1.0.0", "2.0.0"))
        assertFalse(ConfigurationsRepositoryImpl.isVersionAllowed("v1.0.0-lite", "v2.0.0"))
    }

    @Test
    fun parseApkVersionString_should_handle_valid_strings() {
        assertEquals(100, ConfigurationsRepositoryImpl.parseApkVersionString("1.0.0"))
        assertEquals(100, ConfigurationsRepositoryImpl.parseApkVersionString("v1.0.0"))
        assertEquals(123, ConfigurationsRepositoryImpl.parseApkVersionString("1.2.3"))
        assertEquals(12, ConfigurationsRepositoryImpl.parseApkVersionString("0.1.2"))
    }

    @Test
    fun parseApkVersionString_should_return_null_for_empty_or_null_input() {
        assertNull(ConfigurationsRepositoryImpl.parseApkVersionString(null))
        assertNull(ConfigurationsRepositoryImpl.parseApkVersionString(""))
    }
}
