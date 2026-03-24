package org.ole.planet.myplanet.services

import android.content.Context
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import dagger.hilt.android.EntryPointAccessors
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.di.AutoSyncEntryPoint
import org.ole.planet.myplanet.utils.ThemeMode

object ThemeManager {
    private fun getSpm(context: Context): SharedPrefManager =
        EntryPointAccessors.fromApplication(context.applicationContext, AutoSyncEntryPoint::class.java).sharedPrefManager()

    fun showThemeDialog(context: Context) {
        val options = arrayOf(
            context.getString(R.string.theme_mode_light),
            context.getString(R.string.theme_mode_dark),
            context.getString(R.string.dark_mode_follow_system)
        )
        val currentMode = getCurrentThemeMode(context)
        val checkedItem = when (currentMode) {
            ThemeMode.LIGHT -> 0
            ThemeMode.DARK -> 1
            else -> 2
        }
        val singleChoiceAdapter = object : ArrayAdapter<String>(
            context,
            android.R.layout.simple_list_item_single_choice,
            options
        ) {
            override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                val text = view.findViewById<TextView>(android.R.id.text1)
                text.setTextColor(ContextCompat.getColor(context, R.color.daynight_textColor))
                return view
            }
        }
        val builder = AlertDialog.Builder(context, R.style.AlertDialogTheme)
            .setTitle(context.getString(R.string.select_theme_mode))
            .setSingleChoiceItems(singleChoiceAdapter, checkedItem) { dialog, which ->
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

    fun getCurrentThemeMode(context: Context): String =
        getSpm(context).getRawString("theme_mode", ThemeMode.FOLLOW_SYSTEM)

    fun setThemeMode(context: Context, themeMode: String) {
        getSpm(context).setRawString("theme_mode", themeMode)
        AppCompatDelegate.setDefaultNightMode(
            when (themeMode) {
                ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
