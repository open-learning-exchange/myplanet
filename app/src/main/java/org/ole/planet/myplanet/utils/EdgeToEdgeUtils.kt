package org.ole.planet.myplanet.utils

import android.graphics.Color
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.graphics.drawable.toDrawable

object EdgeToEdgeUtils {
    private val SAFE_AREA = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

    fun setupEdgeToEdge(activity: ComponentActivity, rootView: View, lightStatusBar: Boolean? = null,
                        lightNavigationBar: Boolean? = null, statusBarColor: Int? = null
    ) {
        configureEdgeToEdge(activity, rootView, lightStatusBar, lightNavigationBar)
        val statusBarStrip = statusBarColor?.let { paintStatusBarStrip(rootView, it) }

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(SAFE_AREA)
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            statusBarStrip?.let {
                it.setLayerHeight(STRIP_LAYER, insets.top)
                it.invalidateSelf()
            }
            WindowInsetsCompat.CONSUMED
        }
    }

    fun setupEdgeToEdgeWithKeyboard(
        activity: ComponentActivity,
        rootView: View,
        lightStatusBar: Boolean? = null,
        lightNavigationBar: Boolean? = null
    ) {
        configureEdgeToEdge(activity, rootView, lightStatusBar, lightNavigationBar)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val safeInsets = windowInsets.getInsets(SAFE_AREA)
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())

            view.setPadding(
                safeInsets.left,
                safeInsets.top,
                safeInsets.right,
                maxOf(safeInsets.bottom, imeInsets.bottom)
            )
            WindowInsetsCompat.CONSUMED
        }
    }
    
    fun keepClearOfSystemBars(view: View) {
        val base = (view.layoutParams as? ViewGroup.MarginLayoutParams) ?: return
        val left = base.leftMargin
        val top = base.topMargin
        val right = base.rightMargin
        val bottom = base.bottomMargin

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val insets = windowInsets.getInsets(SAFE_AREA)
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                setMargins(left + insets.left, top + insets.top, right + insets.right, bottom + insets.bottom)
            }
            windowInsets
        }
    }

    private const val STRIP_LAYER = 1

    private fun paintStatusBarStrip(rootView: View, color: Int): LayerDrawable {
        val base = rootView.background ?: Color.TRANSPARENT.toDrawable()
        return LayerDrawable(arrayOf(base, color.toDrawable())).apply {
            setLayerGravity(STRIP_LAYER, Gravity.TOP or Gravity.FILL_HORIZONTAL)
            setLayerHeight(STRIP_LAYER, 0)
            rootView.background = this
        }
    }

    private fun configureEdgeToEdge(
        activity: ComponentActivity,
        rootView: View,
        lightStatusBar: Boolean?,
        lightNavigationBar: Boolean?
    ) {
        activity.enableEdgeToEdge()

        if (lightStatusBar == null && lightNavigationBar == null) return
        val controller = WindowCompat.getInsetsController(activity.window, rootView)
        lightStatusBar?.let { controller.isAppearanceLightStatusBars = it }
        lightNavigationBar?.let { controller.isAppearanceLightNavigationBars = it }
    }
}
