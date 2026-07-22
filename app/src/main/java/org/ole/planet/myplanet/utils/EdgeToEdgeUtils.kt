package org.ole.planet.myplanet.utils

import android.graphics.Color
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object EdgeToEdgeUtils {
    private const val TRANSPARENT_SCRIM = Color.TRANSPARENT

    /**
     * Sets up edge-to-edge display with transparent system bars and proper window insets handling.
     * Works across all supported SDK versions (26-36).
     * @param activity The activity to apply edge-to-edge to
     * @param rootView The root view that should handle window insets
     * @param lightStatusBar Whether to use light status bar icons (default: true)
     * @param lightNavigationBar Whether to use light navigation bar icons (default: true)
     */
    fun setupEdgeToEdge(
        activity: ComponentActivity,
        rootView: View,
        lightStatusBar: Boolean = true,
        lightNavigationBar: Boolean = true
    ) {
        configureEdgeToEdge(activity, lightStatusBar, lightNavigationBar)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }
    }

    /**
     * Sets up edge-to-edge with keyboard handling.
     */
    fun setupEdgeToEdgeWithKeyboard(
        activity: ComponentActivity,
        rootView: View,
        lightStatusBar: Boolean = true,
        lightNavigationBar: Boolean = true
    ) {
        configureEdgeToEdge(activity, lightStatusBar, lightNavigationBar)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val systemBarsInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())

            view.setPadding(
                systemBarsInsets.left,
                systemBarsInsets.top,
                systemBarsInsets.right,
                maxOf(systemBarsInsets.bottom, imeInsets.bottom)
            )
            windowInsets
        }
    }

    private fun configureEdgeToEdge(
        activity: ComponentActivity,
        lightStatusBar: Boolean,
        lightNavigationBar: Boolean
    ) {
        activity.enableEdgeToEdge(
            statusBarStyle = systemBarStyle(lightStatusBar),
            navigationBarStyle = systemBarStyle(lightNavigationBar)
        )
    }

    private fun systemBarStyle(useDarkIcons: Boolean): SystemBarStyle = if (useDarkIcons) {
        SystemBarStyle.light(TRANSPARENT_SCRIM, TRANSPARENT_SCRIM)
    } else {
        SystemBarStyle.dark(TRANSPARENT_SCRIM)
    }
}
