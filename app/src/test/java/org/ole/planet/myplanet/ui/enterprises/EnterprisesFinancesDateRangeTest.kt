package org.ole.planet.myplanet.ui.enterprises

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnterprisesFinancesDateRangeTest {

    private val zone = ZoneId.of("Africa/Nairobi")

    private fun millis(dateTime: LocalDateTime) = dateTime.atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `range includes a transaction recorded during the To day`() {
        val (start, end) = EnterprisesFinancesFragment.inclusiveDayRange(
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), zone
        )
        val afternoonOnToDay = millis(LocalDateTime.of(2026, 9, 5, 15, 30))

        assertTrue(afternoonOnToDay in start..end)
    }

    @Test
    fun `same From and To day covers that whole day and nothing after`() {
        val day = LocalDate.of(2026, 9, 5)
        val (start, end) = EnterprisesFinancesFragment.inclusiveDayRange(day, day, zone)

        assertEquals(millis(day.atStartOfDay()), start)
        assertEquals(millis(day.plusDays(1).atStartOfDay()) - 1, end)
    }
}
