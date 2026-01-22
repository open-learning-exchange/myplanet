package org.ole.planet.myplanet.utils

import android.content.res.Resources
import android.util.DisplayMetrics
import kotlin.math.roundToInt

object DimenUtils {
    @JvmStatic
    fun dpToPx(dp: Int): Int {
        val displayMetrics = Resources.getSystem().displayMetrics
        return (dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT)).roundToInt()
    }
}
