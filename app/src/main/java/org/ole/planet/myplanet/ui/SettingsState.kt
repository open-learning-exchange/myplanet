package org.ole.planet.myplanet.ui

sealed class SettingsState {
    object ToDashboard : SettingsState()
    object ToLogin : SettingsState()
}
