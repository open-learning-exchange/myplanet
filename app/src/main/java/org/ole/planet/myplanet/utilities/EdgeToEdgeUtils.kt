package org.ole.planet.myplanet.utilities

import android.app.Activity
import android.graphics.Color
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
        // Enable edge-to-edge using WindowCompat for better compatibility
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        
        // Set transparent system bars
        activity.window.setTransparentSystemBars()
        
        // Configure system bar appearance
        val controller = WindowCompat.getInsetsController(activity.window, rootView)
        controller.isAppearanceLightStatusBars = lightStatusBar
        controller.isAppearanceLightNavigationBars = lightNavigationBar

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
    fun setupEdgeToEdge(
        activity: Activity, 
        rootView: View, 
        lightStatusBar: Boolean = true, 
        lightNavigationBar: Boolean = true, 
        customInsetsHandler: (View, WindowInsetsCompat) -> WindowInsetsCompat
    ) {
        // Enable edge-to-edge using WindowCompat
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        
        // Set transparent system bars
        activity.window.setTransparentSystemBars()
        
        // Configure system bar appearance
        val controller = WindowCompat.getInsetsController(activity.window, rootView)
        controller.isAppearanceLightStatusBars = lightStatusBar
        controller.isAppearanceLightNavigationBars = lightNavigationBar

        // Set up custom window insets listener
        ViewCompat.setOnApplyWindowInsetsListener(rootView, customInsetsHandler)
    }

    /**
     * Extension function to set transparent system bars with proper SDK handling
     */
    private fun Window.setTransparentSystemBars() {
        statusBarColor = Color.TRANSPARENT
        navigationBarColor = Color.TRANSPARENT
    }

    /**
     * Sets up edge-to-edge with only top padding (for activities with bottom navigation)
     */
    fun setupEdgeToEdgeWithTopPadding(
        activity: Activity, 
        rootView: View, 
        lightStatusBar: Boolean = true, 
        lightNavigationBar: Boolean = true
    ) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.window.setTransparentSystemBars()
        
        val controller = WindowCompat.getInsetsController(activity.window, rootView)
        controller.isAppearanceLightStatusBars = lightStatusBar
        controller.isAppearanceLightNavigationBars = lightNavigationBar

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, 0)
            WindowInsetsCompat.CONSUMED
        }
    }

    /**
     * Sets up edge-to-edge with only bottom padding (for activities with toolbar)
     */
    fun setupEdgeToEdgeWithBottomPadding(
        activity: Activity, 
        rootView: View, 
        lightStatusBar: Boolean = true, 
        lightNavigationBar: Boolean = true
    ) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.window.setTransparentSystemBars()
        
        val controller = WindowCompat.getInsetsController(activity.window, rootView)
        controller.isAppearanceLightStatusBars = lightStatusBar
        controller.isAppearanceLightNavigationBars = lightNavigationBar

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, 0, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    /**
     * Sets up edge-to-edge with no padding (for activities that handle insets manually)
     */
    fun setupEdgeToEdgeWithNoPadding(
        activity: Activity, 
        rootView: View, 
        lightStatusBar: Boolean = true, 
        lightNavigationBar: Boolean = true
    ) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.window.setTransparentSystemBars()
        
        val controller = WindowCompat.getInsetsController(activity.window, rootView)
        controller.isAppearanceLightStatusBars = lightStatusBar
        controller.isAppearanceLightNavigationBars = lightNavigationBar

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
        lightStatusBar: Boolean = true, 
        lightNavigationBar: Boolean = true
    ) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.window.setTransparentSystemBars()
        
        val controller = WindowCompat.getInsetsController(activity.window, rootView)
        controller.isAppearanceLightStatusBars = lightStatusBar
        controller.isAppearanceLightNavigationBars = lightNavigationBar

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
}