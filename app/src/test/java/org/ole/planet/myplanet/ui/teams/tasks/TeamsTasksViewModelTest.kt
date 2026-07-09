package org.ole.planet.myplanet.ui.teams.tasks

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.utils.TimeUtils

class TeamsTasksViewModelTest {

    private lateinit var viewModel: TeamsTasksViewModel

    @Before
    fun setUp() {
        viewModel = TeamsTasksViewModel()
    }

    @Test
    fun `initial state is null and getFormatted methods use current time fallback`() {
        assertNull(viewModel.deadline.value)

        val beforeMillis = Calendar.getInstance().timeInMillis
        val formattedDate = viewModel.getFormattedDeadlineDate()
        val formattedTime = viewModel.getFormattedDeadlineWithTime()

        // The fallback uses current time
        val deadlineMillis = viewModel.getDeadlineMillis()
        val afterMillis = Calendar.getInstance().timeInMillis
        assertTrue(deadlineMillis in beforeMillis..afterMillis)

        assertNotNull(formattedDate)
        assertNotNull(formattedTime)
    }

    @Test
    fun `setDeadlineDate correctly updates the calendar year, month, and day`() {
        viewModel.setDeadlineDate(2025, 5, 15) // Month is 0-indexed, so 5 is June

        val deadline = viewModel.deadline.value
        assertNotNull(deadline)
        assertEquals(2025, deadline?.get(Calendar.YEAR))
        assertEquals(5, deadline?.get(Calendar.MONTH))
        assertEquals(15, deadline?.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `setDeadlineTime updates existing calendar if present`() {
        viewModel.setDeadlineDate(2025, 5, 15)
        viewModel.setDeadlineTime(14, 30)

        val deadline = viewModel.deadline.value
        assertNotNull(deadline)
        assertEquals(2025, deadline?.get(Calendar.YEAR))
        assertEquals(5, deadline?.get(Calendar.MONTH))
        assertEquals(15, deadline?.get(Calendar.DAY_OF_MONTH))
        assertEquals(14, deadline?.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, deadline?.get(Calendar.MINUTE))
    }

    @Test
    fun `setDeadlineTime falls back to current date if calendar is not present`() {
        viewModel.setDeadlineTime(14, 30)

        val deadline = viewModel.deadline.value
        assertNotNull(deadline)
        assertEquals(14, deadline?.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, deadline?.get(Calendar.MINUTE))
    }

    @Test
    fun `clearDeadline resets the state to null`() {
        viewModel.setDeadlineDate(2025, 5, 15)
        assertNotNull(viewModel.deadline.value)

        viewModel.clearDeadline()
        assertNull(viewModel.deadline.value)
    }

    @Test
    fun `setDeadline updates from long timestamp`() {
        val calendar = Calendar.getInstance()
        calendar.set(2026, 1, 1, 12, 0, 0)
        val millis = calendar.timeInMillis

        viewModel.setDeadline(millis)

        val deadline = viewModel.deadline.value
        assertNotNull(deadline)
        assertEquals(millis, deadline?.timeInMillis)
    }
}
