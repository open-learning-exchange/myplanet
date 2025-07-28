package org.ole.planet.myplanet.utilities

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object EdgeToEdgeUtil {
    fun setupEdgeToEdge(activity: Activity, rootView: View, lightStatusBar: Boolean = true, lightNavigationBar: Boolean = true) {
        activity.window.enableEdgeToEdge(lightStatusBar, lightNavigationBar)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    fun setupEdgeToEdge(activity: Activity, rootView: View, lightStatusBar: Boolean = true, lightNavigationBar: Boolean = true, customInsetsHandler: (View, WindowInsetsCompat) -> WindowInsetsCompat) {
        activity.window.enableEdgeToEdge(lightStatusBar, lightNavigationBar)
        ViewCompat.setOnApplyWindowInsetsListener(rootView, customInsetsHandler)
    }

    private fun Window.enableEdgeToEdge(lightStatusBar: Boolean = true, lightNavigationBar: Boolean = true) {
        var flags = decorView.systemUiVisibility
        if (lightStatusBar) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        if (lightNavigationBar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }

        decorView.systemUiVisibility = flags
    }

    fun setupEdgeToEdgeWithTopPadding(activity: Activity, rootView: View, lightStatusBar: Boolean = true, lightNavigationBar: Boolean = true) {
        activity.window.enableEdgeToEdge(lightStatusBar, lightNavigationBar)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, 0)
            WindowInsetsCompat.CONSUMED
        }
    }

    fun setupEdgeToEdgeWithBottomPadding(activity: Activity, rootView: View, lightStatusBar: Boolean = true, lightNavigationBar: Boolean = true) {
        activity.window.enableEdgeToEdge(lightStatusBar, lightNavigationBar)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, 0, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }
}
