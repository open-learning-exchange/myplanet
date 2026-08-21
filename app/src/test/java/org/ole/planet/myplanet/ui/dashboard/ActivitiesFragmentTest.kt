package org.ole.planet.myplanet.ui.dashboard

import java.util.Calendar
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.OfflineActivity
import org.ole.planet.myplanet.utils.TestDispatcherProvider

class ActivitiesFragmentTest {

    private lateinit var fragment: ActivitiesFragment

    @Before
    fun setup() {
        fragment = ActivitiesFragment()
        fragment.dispatcherProvider = TestDispatcherProvider(UnconfinedTestDispatcher())
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

    @Test
    fun computeMonthlyCounts_emptyList_returnsEmptyMap() = runTest {
        val startMillis = 1000L
        val endMillis = 5000L

        val result = fragment.computeMonthlyCounts(emptyList(), startMillis, endMillis)

        assertTrue(result.isEmpty())
    }

    @Test
    fun computeMonthlyCounts_filtersOutOfRangeLoginsAndGroupsCorrectly() = runTest {
        val calendar = Calendar.getInstance()

        // Month 0 (January)
        calendar.set(2026, Calendar.JANUARY, 15, 12, 0, 0)
        val janTime1 = calendar.timeInMillis
        calendar.set(2026, Calendar.JANUARY, 20, 14, 0, 0)
        val janTime2 = calendar.timeInMillis

        // Month 2 (March)
        calendar.set(2026, Calendar.MARCH, 10, 10, 0, 0)
        val marchTime = calendar.timeInMillis

        // Out of range (earlier)
        calendar.set(2024, Calendar.JANUARY, 1, 0, 0, 0)
        val outOfRangeTime = calendar.timeInMillis

        val logins = listOf(
            OfflineActivity().apply { id = "1"; userName = "test"; loginTime = janTime1 },
            OfflineActivity().apply { id = "2"; userName = "test"; loginTime = janTime2 },
            OfflineActivity().apply { id = "3"; userName = "test"; loginTime = marchTime },
            OfflineActivity().apply { id = "4"; userName = "test"; loginTime = outOfRangeTime },
            OfflineActivity().apply { id = "5"; userName = "test"; loginTime = null }
        )

        calendar.set(2025, Calendar.DECEMBER, 31, 23, 59, 59)
        val startMillis = calendar.timeInMillis
        calendar.set(2026, Calendar.DECEMBER, 31, 23, 59, 59)
        val endMillis = calendar.timeInMillis

        val result = fragment.computeMonthlyCounts(logins, startMillis, endMillis)

        assertEquals(2, result.size)
        assertEquals(2, result[Calendar.JANUARY])
        assertEquals(1, result[Calendar.MARCH])
    }
}
