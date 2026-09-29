package org.ole.planet.myplanet.ui.health

import kotlin.math.roundToInt

internal fun parseVitalReading(text: String): Float {
    val value = text.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() } ?: return 0f
    return (value * 10).roundToInt() / 10f
}
