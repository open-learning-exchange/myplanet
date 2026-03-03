package org.ole.planet.myplanet.ui.sync

import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.databinding.DialogServerUrlBinding
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.ServerConfigUtils

enum class CallerContext {
    LOGIN_ACTIVITY,
    DASHBOARD_ACTIVITY,
    SYNC_ACTIVITY,
    OTHER
}

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
        fun onSaveConfigAndContinue(dialog: MaterialDialog, binding: DialogServerUrlBinding, defaultUrl: String)
        fun onClearDataDialog()
    }

    fun checkMinApk(
        scope: CoroutineScope,
        url: String,
        pin: String,
        callerContext: CallerContext,
        serverConfigAction: String,
        currentDialog: MaterialDialog?,
        serverDialogBinding: DialogServerUrlBinding?
    ) {
        scope.launch {
            callback.showProgressDialog()
            val result = configurationsRepository.getMinApk(url, pin)
            callback.dismissProgressDialog()
            when (result) {
                is ConfigurationsRepository.ConfigurationResult.Success -> {
                    handleConfigurationSuccess(
                        result.id, result.code, result.url, result.defaultUrl, result.isAlternativeUrl, callerContext,
                        serverConfigAction, currentDialog, serverDialogBinding
                    )
                }
                is ConfigurationsRepository.ConfigurationResult.Failure -> {
                    callback.setSyncFailed(true)
                    callback.showErrorDialog(result.errorMessage)
                }
            }
        }
    }

    private fun handleConfigurationSuccess(
        id: String,
        code: String,
        url: String,
        defaultUrl: String,
        isAlternativeUrl: Boolean,
        callerContext: CallerContext,
        serverConfigAction: String,
        currentDialog: MaterialDialog?,
        serverDialogBinding: DialogServerUrlBinding?
    ) {
        val savedId = prefData.getConfigurationId()
        callback.setSyncFailed(false)
        when (callerContext) {
            CallerContext.LOGIN_ACTIVITY, CallerContext.DASHBOARD_ACTIVITY -> {
                if (isAlternativeUrl) {
                    ServerConfigUtils.saveAlternativeUrl(url, prefData.getServerPin(), prefData)
                }
                callback.onVersionCheckSuccess()
            }
            else -> {
                if (serverConfigAction == "sync") {
                    if (savedId == null) {
                        prefData.setConfigurationId(id)
                        prefData.setCommunityName(code)
                        currentDialog?.let {
                            callback.onContinueSync(it, url, isAlternativeUrl, defaultUrl)
                        }
                    } else if (id == savedId) {
                        currentDialog?.let {
                            callback.onContinueSync(it, url, isAlternativeUrl, defaultUrl)
                        }
                    } else {
                        callback.onClearDataDialog()
                    }
                } else if (serverConfigAction == "save") {
                    if (savedId == null || id == savedId) {
                        currentDialog?.let {
                            val binding = serverDialogBinding ?: return@let
                            callback.onSaveConfigAndContinue(it, binding, defaultUrl)
                        }
                    } else {
                        callback.onClearDataDialog()
                    }
                }
            }
        }
    }
}
