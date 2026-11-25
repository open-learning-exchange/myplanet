package org.ole.planet.myplanet.utils

import java.util.Locale
import kotlin.math.roundToInt

internal fun parseVitalReading(text: String): Float {
    val value = text.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() } ?: return 0f
    return (value * 10).roundToInt() / 10f
}

internal val TEMPERATURE_RANGE = 30f..45f
internal val PULSE_RANGE = 20f..250f
internal val HEIGHT_RANGE = 1f..250f
internal val WEIGHT_RANGE = 0.5f..500f

internal fun isValidVitalReading(text: String, range: ClosedFloatingPointRange<Float>): Boolean {
    if (text.isBlank()) return true
    val value = parseVitalReading(text.trim())
    return value == 0f || value in range
}

internal fun formatVitalReading(value: Float?): String =
    if (value == null || value == 0f) "" else "%.1f".format(Locale.ROOT, value)
