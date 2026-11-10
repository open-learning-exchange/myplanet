package org.ole.planet.myplanet.ui.dashboard

import java.util.Calendar
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActivitiesFragmentTest {

    private lateinit var fragment: ActivitiesFragment

    @Before
    fun setup() {
        fragment = ActivitiesFragment()
    }

    @Test
    fun getMonth_returnsValidMonthName() {
        val january = fragment.getMonth(Calendar.JANUARY)
        val december = fragment.getMonth(Calendar.DECEMBER)

        assertNotNull(january)
        assertNotNull(december)
        assertTrue(january.isNotBlank())
        assertTrue(december.isNotBlank())
    }
}
