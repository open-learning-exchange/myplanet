package org.ole.planet.myplanet.utilities

import android.app.Activity
import android.view.View
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object EdgeToEdgeUtil {
    fun setupEdgeToEdge(activity: Activity, rootView: View) {
        activity.window.enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    fun setupEdgeToEdge(activity: Activity, rootView: View, customInsetsHandler: (View, WindowInsetsCompat) -> WindowInsetsCompat) {
        activity.window.enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(rootView, customInsetsHandler)
    }

    private fun Window.enableEdgeToEdge() {
        var flags = decorView.systemUiVisibility
        
        flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        
        decorView.systemUiVisibility = flags
    }

    fun setupEdgeToEdgeWithTopPadding(activity: Activity, rootView: View) {
        activity.window.enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, 0)
            WindowInsetsCompat.CONSUMED
        }
    }

    fun setupEdgeToEdgeWithBottomPadding(activity: Activity, rootView: View) {
        activity.window.enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, 0, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }
}
