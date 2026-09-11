package org.ole.planet.myplanet.repository

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SubmissionsRepositoryExporterTest {

    @Test
    fun `dateFormatter formats Instant correctly in fixed timezone`() {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
            .withZone(ZoneId.of("UTC"))

        // 2023-10-15T14:30:00Z -> 1697380200L
        val instant = Instant.ofEpochSecond(1697380200L)
        val formatted = formatter.format(instant)

        assertEquals("2023-10-15 14:30", formatted)
    }
}
