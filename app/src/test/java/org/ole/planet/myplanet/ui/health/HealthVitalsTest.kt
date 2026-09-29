package org.ole.planet.myplanet.ui.health

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthVitalsTest {

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
}
