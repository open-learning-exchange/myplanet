package org.ole.planet.myplanet.utilities

import android.util.DisplayMetrics
import org.ole.planet.myplanet.MainApplication
import kotlin.math.roundToInt

object DimenUtils {
    fun dpToPx(dp: Int): Int {
        val displayMetrics = MainApplication.context.resources.displayMetrics
        return (dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT)).roundToInt()
    }
}
