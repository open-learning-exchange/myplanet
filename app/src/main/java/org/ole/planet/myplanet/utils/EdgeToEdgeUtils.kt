package org.ole.planet.myplanet.utils

import android.app.Activity
import android.content.res.Configuration
import android.view.View
import android.view.Window
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
        lightStatusBar: Boolean = true,
        lightNavigationBar: Boolean = true
    ) {
        configureEdgeToEdge(activity, rootView, lightStatusBar, lightNavigationBar)

        // Set up window insets listener
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(rootView)
    }

    /**
     * Extension function to set transparent system bars with proper SDK handling
     */
    private fun Window.setTransparentSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(this, false)
    }

    /**
     * Sets up edge-to-edge with keyboard handling
     */
    fun setupEdgeToEdgeWithKeyboard(
        activity: Activity,
        rootView: View,
        lightStatusBar: Boolean = true,
        lightNavigationBar: Boolean = true
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
        ViewCompat.requestApplyInsets(rootView)
    }

    /**
     * Sets up edge-to-edge display for activities whose top bar is a custom Toolbar
     * standing in for the native ActionBar (e.g. windowActionBar = false themes).
     * The status-bar inset is added to the toolbar's own top padding so the toolbar's
     * background extends behind the status bar, instead of the window background
     * showing through above it.
     */
    fun setupEdgeToEdgeWithStatusBarToolbar(
        activity: Activity,
        rootView: View,
        toolbar: View,
        lightStatusBar: Boolean = true,
        lightNavigationBar: Boolean = true
    ) {
        configureEdgeToEdge(activity, rootView, lightStatusBar, lightNavigationBar)

        val toolbarBasePaddingTop = toolbar.paddingTop
        val toolbarBasePaddingLeft = toolbar.paddingLeft
        val toolbarBasePaddingRight = toolbar.paddingRight
        val toolbarBasePaddingBottom = toolbar.paddingBottom
        val toolbarBaseHeight = toolbar.minimumHeight

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, 0, insets.right, insets.bottom)
            toolbar.setPadding(
                toolbarBasePaddingLeft,
                toolbarBasePaddingTop + insets.top,
                toolbarBasePaddingRight,
                toolbarBasePaddingBottom
            )
            val desiredHeight = toolbarBaseHeight + insets.top + toolbarBasePaddingBottom
            toolbar.layoutParams?.let { params ->
                if (params.height != desiredHeight) {
                    params.height = desiredHeight
                    toolbar.layoutParams = params
                }
            }
            WindowInsetsCompat.CONSUMED
        }

        ViewCompat.requestApplyInsets(rootView)
    }

    private fun configureEdgeToEdge(
        activity: Activity,
        rootView: View,
        lightStatusBar: Boolean,
        lightNavigationBar: Boolean
    ) {
        activity.window.setTransparentSystemBars()

        val isNightMode = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        val controller = WindowCompat.getInsetsController(activity.window, rootView)
        // Dark mode always gets white status bar icons, regardless of what a given screen requests.
        controller.isAppearanceLightStatusBars = !isNightMode && lightStatusBar
        controller.isAppearanceLightNavigationBars = lightNavigationBar
    }
}
