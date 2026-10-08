package org.ole.planet.myplanet.repository

import io.mockk.mockk
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.utils.TestTimeProvider

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

    @Test
    fun `generatedLine uses injected timeProvider`() {
        val testTimeProvider = TestTimeProvider(1697380200000L)
        val exporter = SubmissionsRepositoryExporter(
            mockk(relaxed = true),
            mockk(),
            mockk(),
            mockk(),
            mockk(),
            testTimeProvider
        )

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
            .withZone(ZoneId.systemDefault())

        val expectedInitial = "Generated: " + formatter.format(Instant.ofEpochMilli(1697380200000L))
        assertEquals(expectedInitial, exporter.generatedLine())

        testTimeProvider.advanceBy(60_000)
        val expectedAdvanced = "Generated: " + formatter.format(Instant.ofEpochMilli(1697380260000L))
        assertEquals(expectedAdvanced, exporter.generatedLine())
    }
}
