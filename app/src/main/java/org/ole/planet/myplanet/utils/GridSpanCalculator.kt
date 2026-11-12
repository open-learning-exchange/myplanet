package org.ole.planet.myplanet.utils

object GridSpanCalculator {
    private const val MIN_COLUMNS = 2
    private const val MAX_COLUMNS = 6
    private const val COLUMN_WIDTH_DP = 165

    fun columnCount(availableWidthDp: Int): Int {
        val columns = availableWidthDp / COLUMN_WIDTH_DP
        return columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
    }
}
