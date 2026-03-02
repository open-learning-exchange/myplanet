package org.ole.planet.myplanet.ui.sync

import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.databinding.DialogServerUrlBinding
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.ServerConfigUtils

class SyncConfigurationCoordinator(
    private val configurationsRepository: ConfigurationsRepository,
    private val prefData: SharedPrefManager,
    private val callback: Callback
) {

    interface Callback {
        fun showProgressDialog()
        fun dismissProgressDialog()
        fun setSyncFailed(failed: Boolean)
        fun showErrorDialog(errorMessage: String)
        fun onVersionCheckSuccess()
        fun onContinueSync(dialog: MaterialDialog, url: String, isAlternativeUrl: Boolean, defaultUrl: String)
        fun onSaveConfigAndContinue(dialog: MaterialDialog, binding: DialogServerUrlBinding, url: String, isAlternativeUrl: Boolean, defaultUrl: String)
        fun onClearDataDialog()

        fun getServerConfigAction(): String
        fun getCurrentDialog(): MaterialDialog?
        fun getServerDialogBinding(): DialogServerUrlBinding?
    }

    fun checkMinApk(scope: CoroutineScope, url: String, pin: String, callerActivity: String) {
        scope.launch {
            callback.showProgressDialog()
            val result = configurationsRepository.getMinApk(url, pin)
            callback.dismissProgressDialog()
            when (result) {
                is ConfigurationsRepository.ConfigurationResult.Success -> {
                    handleConfigurationSuccess(result.id, result.code, result.url, result.defaultUrl, result.isAlternativeUrl, callerActivity)
                }
                is ConfigurationsRepository.ConfigurationResult.Failure -> {
                    callback.setSyncFailed(true)
                    callback.showErrorDialog(result.errorMessage)
                }
            }
        }
    }

    private fun handleConfigurationSuccess(id: String, code: String, url: String, defaultUrl: String, isAlternativeUrl: Boolean, callerActivity: String) {
        val savedId = prefData.getConfigurationId()
        callback.setSyncFailed(false)
        when (callerActivity) {
            "LoginActivity", "DashboardActivity"-> {
                if (isAlternativeUrl) {
                    ServerConfigUtils.saveAlternativeUrl(url, prefData.getServerPin(), prefData)
                }
                callback.onVersionCheckSuccess()
            }
            else -> {
                if (callback.getServerConfigAction() == "sync") {
                    if (savedId == null) {
                        prefData.setConfigurationId(id)
                        prefData.setCommunityName(code)
                        callback.getCurrentDialog()?.let {
                            callback.onContinueSync(it, url, isAlternativeUrl, defaultUrl)
                        }
                    } else if (id == savedId) {
                        callback.getCurrentDialog()?.let {
                            callback.onContinueSync(it, url, isAlternativeUrl, defaultUrl)
                        }
                    } else {
                        callback.onClearDataDialog()
                    }
                } else if (callback.getServerConfigAction() == "save") {
                    if (savedId == null || id == savedId) {
                        callback.getCurrentDialog()?.let {
                            val binding = callback.getServerDialogBinding() ?: return@let
                            callback.onSaveConfigAndContinue(it, binding, "", false, defaultUrl)
                        }
                    } else {
                        callback.onClearDataDialog()
                    }
                }
            }
        }
    }
}
