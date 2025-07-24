package org.ole.planet.myplanet.utilities

import android.content.Context
import android.util.DisplayMetrics
import kotlin.math.roundToInt

object DimenUtils {
    @JvmStatic
    fun dpToPx(context: Context, dp: Int): Int {
        val displayMetrics = context.resources.displayMetrics
        return (dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT)).roundToInt()
    }
}
