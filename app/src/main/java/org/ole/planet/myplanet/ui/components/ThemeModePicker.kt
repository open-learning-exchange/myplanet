package org.ole.planet.myplanet.ui.components

import android.content.Context
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.services.ThemeManager
import org.ole.planet.myplanet.utils.ThemeMode

fun showThemeModePicker(context: Context, themeManager: ThemeManager) {
    val options = arrayOf(
        context.getString(R.string.theme_mode_light),
        context.getString(R.string.theme_mode_dark),
        context.getString(R.string.dark_mode_follow_system)
    )
    val currentMode = themeManager.getCurrentThemeMode()
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
            themeManager.setThemeMode(selectedMode)
            dialog.dismiss()
        }
        .setNegativeButton(R.string.cancel, null)
    val dialog = builder.create()
    dialog.show()
    dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}
