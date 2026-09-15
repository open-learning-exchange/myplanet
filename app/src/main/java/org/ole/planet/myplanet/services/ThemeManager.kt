package org.ole.planet.myplanet.services

import android.content.Context
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import javax.inject.Inject
import javax.inject.Singleton
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.utils.ThemeMode

@Singleton
class ThemeManager @Inject constructor(
    private val sharedPrefManager: SharedPrefManager
) {
    fun showThemeDialog(context: Context) {
        val options = arrayOf(
            context.getString(R.string.theme_mode_light),
            context.getString(R.string.theme_mode_dark),
            context.getString(R.string.dark_mode_follow_system)
        )
        val currentMode = getCurrentThemeMode()
        val checkedItem = when (currentMode) {
            ThemeMode.LIGHT -> 0
            ThemeMode.DARK -> 1
            else -> 2
        }
        val builder = AlertDialog.Builder(context, R.style.AlertDialogTheme)
            .setTitle(context.getString(R.string.select_theme_mode))
            .setSingleChoiceItems(ArrayAdapter(context, R.layout.checked_list_item, options), checkedItem) { dialog, which ->
                val selectedMode = when (which) {
                    0 -> ThemeMode.LIGHT
                    1 -> ThemeMode.DARK
                    2 -> ThemeMode.FOLLOW_SYSTEM
                    else -> ThemeMode.FOLLOW_SYSTEM
                }
                setThemeMode(selectedMode)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
        val dialog = builder.create()
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    fun getCurrentThemeMode(): String =
        sharedPrefManager.getRawString("theme_mode", ThemeMode.FOLLOW_SYSTEM)

    fun setThemeMode(themeMode: String) {
        sharedPrefManager.setRawString("theme_mode", themeMode)
        AppCompatDelegate.setDefaultNightMode(
            when (themeMode) {
                ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
