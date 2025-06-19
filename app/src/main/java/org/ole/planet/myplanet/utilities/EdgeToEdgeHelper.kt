// EdgeToEdgeHelper.kt
package org.ole.planet.myplanet.utilities

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ListView
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R

object EdgeToEdgeHelper {

    /**
     * Enables edge-to-edge display for the activity
     * Should be called in onCreate() before setContentView()
     */
    fun enableEdgeToEdge(activity: Activity) {
        try {
            // Enable edge-to-edge
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)

            // For Android 15+ (API 35+), set additional flags
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                setAndroid15EdgeToEdgeFlags(activity)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            MainApplication.createLog("edge_to_edge_error", "Failed to enable edge-to-edge: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun setAndroid15EdgeToEdgeFlags(activity: Activity) {
        try {
            activity.window.attributes = activity.window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Applies window insets to a root view with smart padding
     * This should be called after setContentView()
     */
    fun applyWindowInsets(rootView: View, applyTop: Boolean = true, applyBottom: Boolean = true) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            view.updatePadding(
                left = insets.left,
                top = if (applyTop) insets.top else 0,
                right = insets.right,
                bottom = if (applyBottom) insets.bottom else 0
            )

            windowInsets
        }
    }

    /**
     * Applies window insets to a scrollable content view
     * This prevents content from being hidden behind system bars while allowing scrolling
     */
    fun applyWindowInsetsToScrollableContent(
        rootView: View,
        contentView: View,
        applyTop: Boolean = true,
        applyBottom: Boolean = true
    ) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            contentView.updatePadding(
                left = insets.left,
                top = if (applyTop) insets.top else 0,
                right = insets.right,
                bottom = if (applyBottom) insets.bottom else 0
            )

            windowInsets
        }
    }

    /**
     * For activities with toolbars/app bars
     * Only applies bottom padding, letting the toolbar handle top insets
     */
    fun applyWindowInsetsWithToolbar(rootView: View) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            view.updatePadding(
                left = insets.left,
                top = 0, // Toolbar handles top insets
                right = insets.right,
                bottom = insets.bottom
            )

            windowInsets
        }
    }

    /**
     * For login screens or full-screen layouts
     * Applies smart padding that adapts to screen size
     */
    fun applyWindowInsetsForLoginScreen(rootView: View) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val displayCutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())

            // Use the maximum of system bars and display cutout
            val leftInset = maxOf(insets.left, displayCutout.left)
            val rightInset = maxOf(insets.right, displayCutout.right)
            val topInset = maxOf(insets.top, displayCutout.top)
            val bottomInset = maxOf(insets.bottom, displayCutout.bottom)

            view.updatePadding(
                left = leftInset,
                top = topInset,
                right = rightInset,
                bottom = bottomInset
            )

            windowInsets
        }
    }

    /**
     * For bottom sheets and dialogs
     * Only applies necessary padding to avoid clipping
     */
    fun applyWindowInsetsForBottomSheet(rootView: View) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Only apply horizontal padding for bottom sheets
            view.updatePadding(
                left = insets.left,
                right = insets.right
            )

            windowInsets
        }
    }

    /**
     * For fragments - applies insets without interfering with parent activity
     */
    fun applyWindowInsetsToFragment(rootView: View, consumeInsets: Boolean = false) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            view.updatePadding(
                left = insets.left,
                right = insets.right,
                bottom = insets.bottom
            )

            // Return consumed insets if requested, otherwise pass them through
            if (consumeInsets) {
                WindowInsetsCompat.CONSUMED
            } else {
                windowInsets
            }
        }
    }


    /**
     * Applies window insets specifically for navigation drawers
     * This ensures the drawer content is not hidden behind system bars
     */
    fun applyWindowInsetsToDrawer(drawerView: View) {
        ViewCompat.setOnApplyWindowInsetsListener(drawerView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Apply padding to ensure content is visible
            view.updatePadding(
                left = 0, // Don't apply left padding as drawer slides from left
                top = insets.top,
                right = 0,
                bottom = insets.bottom // This is crucial for the logout button
            )

            windowInsets
        }
    }

    /**
     * For MaterialDrawer or similar drawer implementations
     */
    fun applyWindowInsetsToMaterialDrawer(drawerSlider: View) {
        ViewCompat.setOnApplyWindowInsetsListener(drawerSlider) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Find the recycler view or list view inside the drawer
            val drawerContent = view.findViewById<RecyclerView>(R.id.material_drawer_recycler_view)
                ?: view.findViewById<ListView>(android.R.id.list)

            drawerContent?.updatePadding(
                top = insets.top,
                bottom = insets.bottom
            )

            windowInsets
        }
    }
}

// Extension functions for easier usage
fun Activity.enableEdgeToEdgeDisplay() {
    EdgeToEdgeHelper.enableEdgeToEdge(this)
}

fun View.applySystemBarInsets(applyTop: Boolean = true, applyBottom: Boolean = true) {
    EdgeToEdgeHelper.applyWindowInsets(this, applyTop, applyBottom)
}

fun View.applyLoginScreenInsets() {
    EdgeToEdgeHelper.applyWindowInsetsForLoginScreen(this)
}

fun View.applyBottomSheetInsets() {
    EdgeToEdgeHelper.applyWindowInsetsForBottomSheet(this)
}

fun View.applyFragmentInsets(consumeInsets: Boolean = false) {
    EdgeToEdgeHelper.applyWindowInsetsToFragment(this, consumeInsets)
}

fun View.applyToolbarInsets() {
    EdgeToEdgeHelper.applyWindowInsetsWithToolbar(this)
}

fun View.applyDrawerInsets() {
    EdgeToEdgeHelper.applyWindowInsetsToDrawer(this)
}

fun View.applyMaterialDrawerInsets() {
    EdgeToEdgeHelper.applyWindowInsetsToMaterialDrawer(this)
}