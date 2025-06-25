package org.ole.planet.myplanet.datamanager

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.ui.sync.SyncActivity
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.Constants.KEY_UPGRADE_MAX
import org.ole.planet.myplanet.utilities.Constants.showBetaFeature
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.VersionUtils
import java.lang.Exception

class VersionManager(
    private val context: Context,
    private val preferences: SharedPreferences,
    private val retrofitInterface: ApiInterface?
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun checkVersion(callback: Service.CheckVersionCallback, settings: SharedPreferences) {
        if (!settings.getBoolean("isAlternativeUrl", false) && settings.getString("couchdbURL", "").isNullOrEmpty()) {
            if (context is SyncActivity) {
                context.settingDialog()
            }
            return
        }
        callback.onCheckingVersion()
        scope.launch {
            try {
                val planetInfo = withContext(Dispatchers.IO) {
                    retrofitInterface?.checkVersion(Utilities.getUpdateUrl(settings))?.execute()?.body()
                }

                preferences.edit {
                    putInt("LastWifiID", NetworkUtils.getCurrentNetworkId(context))
                }

                if (planetInfo != null) {
                    preferences.edit { putString("versionDetail", Gson().toJson(planetInfo)) }
                    val versionString = requestApkVersion(settings)
                    withContext(Dispatchers.Main) { handleVersionInfo(planetInfo, versionString, callback) }
                } else {
                    withContext(Dispatchers.Main) { callback.onError(context.getString(R.string.version_not_found), true) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { callback.onError(context.getString(R.string.connection_failed), true) }
            }
        }
    }

    private suspend fun requestApkVersion(settings: SharedPreferences): String? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val response = retrofitInterface?.getApkVersion(Utilities.getApkVersionUrl(settings))?.execute()
                Gson().fromJson(response?.body()?.string(), String::class.java)
            }.getOrNull()
        }
    }

    private fun handleVersionInfo(info: MyPlanet?, versionString: String?, callback: Service.CheckVersionCallback) {
        val apkVersion = versionString?.let { parseVersionCode(it) }
        if (apkVersion == null) {
            callback.onError(context.getString(R.string.planet_is_up_to_date), false)
            return
        }

        try {
            val currentVersion = VersionUtils.getVersionCode(context)

            info?.let { p ->
                when {
                    showBetaFeature(KEY_UPGRADE_MAX, context) && p.latestapkcode > currentVersion ->
                        callback.onUpdateAvailable(p, false)

                    apkVersion > currentVersion ->
                        callback.onUpdateAvailable(p, currentVersion >= p.minapkcode)

                    currentVersion < p.minapkcode && apkVersion < p.minapkcode ->
                        callback.onUpdateAvailable(p, true)

                    else ->
                        callback.onError(context.getString(R.string.planet_is_up_to_date), false)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            callback.onError(context.getString(R.string.new_apk_version_required_but_not_found_on_server), false)
        }
    }

    private fun parseVersionCode(versionString: String): Int {
        return versionString
            .replace("v", "")
            .replace(".", "")
            .removePrefix("0")
            .toInt()
    }
}
