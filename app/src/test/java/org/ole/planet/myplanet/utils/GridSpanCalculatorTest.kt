package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class GridSpanCalculatorTest {

    @Test
    fun columnCount_belowMinWidth_clampsToMin() {
        assertEquals(2, GridSpanCalculator.columnCount(0))
        assertEquals(2, GridSpanCalculator.columnCount(164))
    }

    @Test
    fun columnCount_atExactMultiple_returnsExactColumns() {
        assertEquals(2, GridSpanCalculator.columnCount(330))
        assertEquals(3, GridSpanCalculator.columnCount(495))
        assertEquals(4, GridSpanCalculator.columnCount(660))
    }

    @Test
    fun columnCount_aboveMaxWidth_clampsToMax() {
        assertEquals(6, GridSpanCalculator.columnCount(990))
        assertEquals(6, GridSpanCalculator.columnCount(5000))
    }

    @Test
    fun columnCount_negativeWidth_clampsToMin() {
        assertEquals(2, GridSpanCalculator.columnCount(-100))
    }
}
