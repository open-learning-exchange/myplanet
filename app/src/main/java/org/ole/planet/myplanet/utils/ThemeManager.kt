package org.ole.planet.myplanet.utils

import android.content.Context
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.utils.Constants.PREFS_NAME

object ThemeManager {
    fun showThemeDialog(context: Context) {
        val options = arrayOf(
            context.getString(R.string.dark_mode_off),
            context.getString(R.string.dark_mode_on),
            context.getString(R.string.dark_mode_follow_system)
        )
        val currentMode = getCurrentThemeMode(context)
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
                setThemeMode(context, selectedMode)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
        val dialog = builder.create()
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    fun getCurrentThemeMode(context: Context): String {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getString("theme_mode", ThemeMode.FOLLOW_SYSTEM) ?: ThemeMode.FOLLOW_SYSTEM
    }

    fun setThemeMode(context: Context, themeMode: String) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString("theme_mode", themeMode)
            apply()
        }
        AppCompatDelegate.setDefaultNightMode(
            when (themeMode) {
                ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
