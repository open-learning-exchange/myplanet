package org.ole.planet.myplanet.utils

import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

fun computePdfRenderSize(
    pageW: Int,
    pageH: Int,
    targetW: Int,
    maxDim: Int = 2048,
    maxPixels: Int = 16_000_000
): Pair<Int, Int> {
    if (pageW <= 0 || pageH <= 0) {
        return Pair(1, 1)
    }

    val (w0, h0) = if (targetW <= 0) {
        Pair(pageW.toDouble(), pageH.toDouble())
    } else {
        val scale = targetW.toDouble() / pageW.toDouble()
        Pair(targetW.toDouble(), pageH.toDouble() * scale)
    }

    val area = w0 * h0
    val scaleMaxDim = min(maxDim.toDouble() / w0, maxDim.toDouble() / h0)
    val scaleMaxPixels = if (area > maxPixels) sqrt(maxPixels.toDouble() / area) else 1.0

    val factor = min(1.0, min(scaleMaxDim, scaleMaxPixels))

    val finalW = (w0 * factor).roundToInt().coerceAtLeast(1)
    val finalH = (h0 * factor).roundToInt().coerceAtLeast(1)

    return Pair(finalW, finalH)
}
