package org.ole.planet.myplanet.utilities;

import android.util.DisplayMetrics;

import org.ole.planet.myplanet.MainApplication;

public class DimenUtils {
    public static int dpToPx(int dp) {
        DisplayMetrics displayMetrics = MainApplication.context.getResources().getDisplayMetrics();
        return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }
}
