package org.ole.planet.myplanet.utilities

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

object EdgeToEdgeUtils {
    /**
     * Sets up edge-to-edge display with transparent system bars and proper window insets handling
     * Works across all supported SDK versions (26-36)
     * @param activity The activity to apply edge-to-edge to
     * @param rootView The root view that should handle window insets
     * @param lightStatusBar Whether to use light status bar icons (default: true)
     * @param lightNavigationBar Whether to use light navigation bar icons (default: true)
     */
    fun setupEdgeToEdge(
        activity: Activity,
        rootView: View,
        lightStatusBar: Boolean = false,
        lightNavigationBar: Boolean = false
    ) {
        configureEdgeToEdge(activity, rootView, lightStatusBar, lightNavigationBar)

        // Set up window insets listener
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    /**
     * Sets up edge-to-edge with no padding (for activities that handle insets manually)
     */
    fun setupEdgeToEdgeWithNoPadding(
        activity: Activity,
        rootView: View,
        lightStatusBar: Boolean = false,
        lightNavigationBar: Boolean = false
    ) {
        configureEdgeToEdge(activity, rootView, lightStatusBar, lightNavigationBar)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, windowInsets ->
            // Return windowInsets without consuming them, allowing child views to handle
            windowInsets
        }
    }

    /**
     * Sets up edge-to-edge with keyboard handling
     */
    fun setupEdgeToEdgeWithKeyboard(
        activity: Activity,
        rootView: View,
        lightStatusBar: Boolean = false,
        lightNavigationBar: Boolean = false
    ) {
        configureEdgeToEdge(activity, rootView, lightStatusBar, lightNavigationBar)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val systemBarsInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())

            view.setPadding(
                systemBarsInsets.left,
                systemBarsInsets.top,
                systemBarsInsets.right,
                maxOf(systemBarsInsets.bottom, imeInsets.bottom)
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun configureEdgeToEdge(
        activity: Activity,
        rootView: View,
        lightStatusBar: Boolean,
        lightNavigationBar: Boolean
    ) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)

        val controller = WindowCompat.getInsetsController(activity.window, rootView)
        controller.isAppearanceLightStatusBars = lightStatusBar
        controller.isAppearanceLightNavigationBars = lightNavigationBar
    }
}
