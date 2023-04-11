package org.ole.planet.myplanet.ui

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ole.planet.myplanet.utilities.Constants

class SplashViewModel(private val preferences: SharedPreferences) : ViewModel() {

    private val mutableSettingsState: MutableStateFlow<SettingsState?> = MutableStateFlow(null)
    val settingsState = mutableSettingsState.asStateFlow()

    fun setSettingsState(hasAutoSyncFeature: Boolean) {
        if (preferences.getBoolean(Constants.KEY_LOGIN, false) && !hasAutoSyncFeature) {
            mutableSettingsState.value = SettingsState.ToDashboard
        }

        if (preferences.contains(Constants.KEY_IS_CHILD)) {
            mutableSettingsState.value = SettingsState.ToLogin
        }
    }

}