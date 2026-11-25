package org.ole.planet.myplanet.ui.components

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BirthDatePickerTest {

    private val today = LocalDate.of(2026, 11, 20)

    @Test
    fun `this year's month wheel stops at the current month`() {
        assertEquals(11, BirthDatePicker.lastMonth(2026, today))
        assertEquals(12, BirthDatePicker.lastMonth(2000, today))
    }

    @Test
    fun `day wheel follows the month's length`() {
        assertEquals(30, BirthDatePicker.lastDay(2000, 11, today))
        assertEquals(29, BirthDatePicker.lastDay(2000, 2, today))
        assertEquals(28, BirthDatePicker.lastDay(2001, 2, today))
        assertEquals(31, BirthDatePicker.lastDay(2000, 1, today))
    }

    @Test
    fun `the current month's day wheel stops at today`() {
        assertEquals(20, BirthDatePicker.lastDay(2026, 11, today))
    }

    @Test
    fun `reads every format the forms keep`() {
        assertEquals(LocalDate.of(2000, 11, 15), BirthDatePicker.parse("2000-11-15"))
        assertEquals(LocalDate.of(2000, 11, 15), BirthDatePicker.parse("15-11-2000"))
        assertEquals(LocalDate.of(2000, 11, 15), BirthDatePicker.parse("2000-11-15T00:00:00.000Z"))
    }

    @Test
    fun `placeholder text and blanks mean no date yet`() {
        assertNull(BirthDatePicker.parse(null))
        assertNull(BirthDatePicker.parse(""))
        assertNull(BirthDatePicker.parse("Birth Date"))
        assertNull(BirthDatePicker.parse("2000-13-45"))
    }
}
