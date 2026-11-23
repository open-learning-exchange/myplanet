package org.ole.planet.myplanet.services

import androidx.appcompat.app.AppCompatDelegate
import javax.inject.Inject
import javax.inject.Singleton
import org.ole.planet.myplanet.utils.ThemeMode

@Singleton
class ThemeManager @Inject constructor(
    private val sharedPrefManager: SharedPrefManager
) {
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
