package org.ole.planet.myplanet.utilities

import android.util.DisplayMetrics
import kotlin.math.roundToInt
import org.ole.planet.myplanet.utilities.Utilities

object DimenUtils {
    @JvmStatic
    fun dpToPx(dp: Int): Int {
        val displayMetrics = Utilities.context.resources.displayMetrics
        return (dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT)).roundToInt()
    }
}
