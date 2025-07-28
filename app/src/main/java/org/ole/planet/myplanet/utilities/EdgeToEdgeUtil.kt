package org.ole.planet.myplanet.utilities

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object EdgeToEdgeUtil {
    /**
     * Sets up edge-to-edge display with transparent system bars and proper window insets handling
     * @param activity The activity to apply edge-to-edge to
     * @param rootView The root view that should handle window insets
     * @param lightStatusBar Whether to use light status bar icons (default: true)
     * @param lightNavigationBar Whether to use light navigation bar icons (default: true)
     */
    fun setupEdgeToEdge(activity: Activity, rootView: View, lightStatusBar: Boolean = true, lightNavigationBar: Boolean = true) {
        // Enable edge-to-edge
        activity.window.enableEdgeToEdge(lightStatusBar, lightNavigationBar)

        // Set up window insets listener
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    /**
     * Sets up edge-to-edge display with custom inset handling
     * @param activity The activity to apply edge-to-edge to
     * @param rootView The root view that should handle window insets
     * @param lightStatusBar Whether to use light status bar icons (default: true)
     * @param lightNavigationBar Whether to use light navigation bar icons (default: true)
     * @param customInsetsHandler Custom handler for window insets
     */
    fun setupEdgeToEdge(activity: Activity, rootView: View, lightStatusBar: Boolean = true, lightNavigationBar: Boolean = true, customInsetsHandler: (View, WindowInsetsCompat) -> WindowInsetsCompat) {
        // Enable edge-to-edge
        activity.window.enableEdgeToEdge(lightStatusBar, lightNavigationBar)

        // Set up custom window insets listener
        ViewCompat.setOnApplyWindowInsetsListener(rootView, customInsetsHandler)
    }

    /**
     * Extension function to enable edge-to-edge on a Window
     */
    private fun Window.enableEdgeToEdge(lightStatusBar: Boolean = true, lightNavigationBar: Boolean = true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+)
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
        } else {
            // Android 10 and below
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT

            // Control system bar icon colors for older versions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                var flags = decorView.systemUiVisibility

                if (lightStatusBar) {
                    flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }

                if (lightNavigationBar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                }

                decorView.systemUiVisibility = flags
            }
        }
    }

    /**
     * Sets up edge-to-edge with only top padding (for activities with bottom navigation)
     */
    fun setupEdgeToEdgeWithTopPadding(activity: Activity, rootView: View, lightStatusBar: Boolean = true, lightNavigationBar: Boolean = true) {
        activity.window.enableEdgeToEdge(lightStatusBar, lightNavigationBar)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, 0)
            WindowInsetsCompat.CONSUMED
        }
    }

    /**
     * Sets up edge-to-edge with only bottom padding (for activities with toolbar)
     */
    fun setupEdgeToEdgeWithBottomPadding(activity: Activity, rootView: View, lightStatusBar: Boolean = true, lightNavigationBar: Boolean = true) {
        activity.window.enableEdgeToEdge(lightStatusBar, lightNavigationBar)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, 0, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }
}
