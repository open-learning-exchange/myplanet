package org.ole.planet.myplanet.utils

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VitalReadingUtilsTest {

    @Test
    fun parseVitalReading_commaDecimalLocale_keepsDecimalVitals() {
        val originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.FRANCE)
        try {
            assertEquals(36.6f, parseVitalReading("36.6"), 0f)
            assertEquals(36.6f, parseVitalReading("36,6"), 0f)
            assertEquals(72.5f, parseVitalReading("72.46"), 0f)
            assertEquals(170f, parseVitalReading("170"), 0f)
            assertEquals(0f, parseVitalReading(""), 0f)
            assertEquals(0f, parseVitalReading("abc"), 0f)
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun isValidVitalReading_blankOrZero_isTreatedAsNotRecorded() {
        assertTrue(isValidVitalReading("", HEIGHT_RANGE))
        assertTrue(isValidVitalReading("   ", WEIGHT_RANGE))
        assertTrue(isValidVitalReading("0", PULSE_RANGE))
    }

    @Test
    fun isValidVitalReading_acceptsInfantAndFeverReadings() {
        assertTrue(isValidVitalReading("130", PULSE_RANGE))
        assertTrue(isValidVitalReading("40,8", TEMPERATURE_RANGE))
        assertTrue(isValidVitalReading("3.2", WEIGHT_RANGE))
        assertTrue(isValidVitalReading("180", WEIGHT_RANGE))
    }

    @Test
    fun isValidVitalReading_rejectsReadingsOutsideRange() {
        assertFalse(isValidVitalReading("98.6", TEMPERATURE_RANGE))
        assertFalse(isValidVitalReading("300", PULSE_RANGE))
        assertFalse(isValidVitalReading("600", WEIGHT_RANGE))
        assertFalse(isValidVitalReading("251", HEIGHT_RANGE))
    }

    @Test
    fun formatVitalReading_leavesUnrecordedBlank() {
        assertEquals("", formatVitalReading(0f))
        assertEquals("", formatVitalReading(null))
        assertEquals("36.6", formatVitalReading(36.6f))
    }
}
