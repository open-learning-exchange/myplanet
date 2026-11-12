package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ChallengeUtilsTest {

    @Test
    fun `calculateIndividualProgress handles boundary cases properly`() {
        assertEquals(1, calculateIndividualProgress(0, false))
        assertEquals(0, calculateIndividualProgress(0, true))

        assertEquals(3, calculateIndividualProgress(1, false))
        assertEquals(2, calculateIndividualProgress(1, true))

        assertEquals(11, calculateIndividualProgress(5, false))
        assertEquals(10, calculateIndividualProgress(5, true))

        // Capped voice count cases
        assertEquals(11, calculateIndividualProgress(10, false))
        assertEquals(10, calculateIndividualProgress(10, true))
    }

    @Test
    fun `calculateCommunityProgress handles boundary cases properly`() {
        assertEquals(1, calculateCommunityProgress(0, false))
        assertEquals(0, calculateCommunityProgress(0, true))

        assertEquals(3, calculateCommunityProgress(1, false))
        assertEquals(2, calculateCommunityProgress(1, true))

        assertEquals(11, calculateCommunityProgress(5, false))
        assertEquals(10, calculateCommunityProgress(5, true))

        // Capped voice count cases
        assertEquals(11, calculateCommunityProgress(10, false))
        assertEquals(10, calculateCommunityProgress(10, true))
    }
}
