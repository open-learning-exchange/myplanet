package org.ole.planet.myplanet.utilities

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.ole.planet.myplanet.R

object EdgeToEdgeUtil {
    
    fun enableEdgeToEdge(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val window: Window = activity.window
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            
            val primaryDarkColor = ContextCompat.getColor(activity, R.color.colorPrimaryDark)
            val primaryColor = ContextCompat.getColor(activity, R.color.colorPrimary)
            window.statusBarColor = primaryDarkColor
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.navigationBarColor = primaryColor
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.attributes.layoutInDisplayCutoutMode = 
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            
            val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
            windowInsetsController.isAppearanceLightStatusBars = false
            windowInsetsController.isAppearanceLightNavigationBars = false
        }
    }
    
    fun applyWindowInsets(rootView: View, applyTopInsets: Boolean = true, applyBottomInsets: Boolean = true) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            
            view.setPadding(
                systemBars.left,
                if (applyTopInsets) systemBars.top else 0,
                systemBars.right,
                if (applyBottomInsets) maxOf(systemBars.bottom, imeInsets.bottom) else 0
            )
            insets
        }
    }
}