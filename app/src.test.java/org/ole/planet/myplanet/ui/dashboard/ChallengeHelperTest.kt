package org.ole.planet.myplanet.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class ChallengeHelperTest {
    @Test
    fun `getDateFromTimestamp should format timestamp correctly`() {
        val timestamp = 1672531199000 // Represents 2022-12-31 23:59:59 GMT
        val expectedDate = "2022-12-31"
        val actualDate = ChallengeHelper.getDateFromTimestamp(timestamp)
        assertEquals(expectedDate, actualDate)
    }
}