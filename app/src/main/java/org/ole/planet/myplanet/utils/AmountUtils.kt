package org.ole.planet.myplanet.utils

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

object AmountUtils {
    fun parse(text: CharSequence?): Double? =
        text?.toString()?.trim()?.replace(',', '.')?.toDoubleOrNull()

    fun toPlainString(value: Double): String =
        BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    fun format(value: Double): String {
        val rounded = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)
        return if (rounded.stripTrailingZeros().scale() <= 0) {
            String.format(Locale.getDefault(), "%d", rounded.toLong())
        } else {
            String.format(Locale.getDefault(), "%.2f", rounded)
        }
    }
}
