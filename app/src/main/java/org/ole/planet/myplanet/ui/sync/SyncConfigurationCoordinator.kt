package org.ole.planet.myplanet.ui.sync

import android.content.SharedPreferences
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.utils.ServerConfigUtils

class SyncConfigurationCoordinator(
    private val configurationsRepository: ConfigurationsRepository,
    private val settings: SharedPreferences,
    private val editor: SharedPreferences.Editor
) {

    interface Callback {
        fun onCheckStarted()
        fun onCheckComplete()
        fun onCheckFailed(errorMessage: String)
        fun onVersionCheckRequired(isAlternativeUrl: Boolean, url: String)
        fun onContinueSync(id: String, code: String, url: String, isAlternativeUrl: Boolean, defaultUrl: String)
        fun onSaveConfigAndContinue(id: String, url: String, isAlternativeUrl: Boolean, defaultUrl: String)
        fun onClearDataRequested(message: String, config: Boolean)
    }

    suspend fun checkMinApk(
        url: String,
        pin: String,
        callerActivity: String,
        serverConfigAction: String,
        hasCurrentDialog: Boolean,
        callback: Callback
    ) {
        callback.onCheckStarted()
        val result = configurationsRepository.getMinApk(url, pin)
        callback.onCheckComplete()

        when (result) {
            is ConfigurationsRepository.ConfigurationResult.Success -> {
                handleConfigurationSuccess(
                    id = result.id,
                    code = result.code,
                    url = result.url,
                    defaultUrl = result.defaultUrl,
                    isAlternativeUrl = result.isAlternativeUrl,
                    callerActivity = callerActivity,
                    serverConfigAction = serverConfigAction,
                    hasCurrentDialog = hasCurrentDialog,
                    callback = callback
                )
            }
            is ConfigurationsRepository.ConfigurationResult.Failure -> {
                callback.onCheckFailed(result.errorMessage)
            }
        }
    }

    private fun handleConfigurationSuccess(
        id: String,
        code: String,
        url: String,
        defaultUrl: String,
        isAlternativeUrl: Boolean,
        callerActivity: String,
        serverConfigAction: String,
        hasCurrentDialog: Boolean,
        callback: Callback
    ) {
        val savedId = settings.getString("configurationId", null)

        when (callerActivity) {
            "LoginActivity", "DashboardActivity" -> {
                if (isAlternativeUrl) {
                    ServerConfigUtils.saveAlternativeUrl(
                        url,
                        settings.getString("serverPin", "") ?: "",
                        settings,
                        editor
                    )
                }
                callback.onVersionCheckRequired(isAlternativeUrl, url)
            }
            else -> {
                if (serverConfigAction == "sync") {
                    if (savedId == null) {
                        editor.putString("configurationId", id).apply()
                        editor.putString("communityName", code).apply()
                        if (hasCurrentDialog) {
                            callback.onContinueSync(id, code, url, isAlternativeUrl, defaultUrl)
                        }
                    } else if (id == savedId) {
                        if (hasCurrentDialog) {
                            callback.onContinueSync(id, code, url, isAlternativeUrl, defaultUrl)
                        }
                    } else {
                        callback.onClearDataRequested("You want to connect to a different server", false)
                    }
                } else if (serverConfigAction == "save") {
                    if (savedId == null || id == savedId) {
                        if (hasCurrentDialog) {
                            callback.onSaveConfigAndContinue(id, url, isAlternativeUrl, defaultUrl)
                        }
                    } else {
                        callback.onClearDataRequested("You want to connect to a different server", false)
                    }
                }
            }
        }
    }
}
