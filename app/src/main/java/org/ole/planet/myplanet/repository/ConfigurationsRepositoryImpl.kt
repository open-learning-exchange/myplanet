package org.ole.planet.myplanet.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.data.NetworkResult
import org.ole.planet.myplanet.data.api.ApiClient
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.VersionUtils

class ConfigurationsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiInterface: ApiInterface,
    @ApplicationScope private val serviceScope: CoroutineScope,
    @AppPreferences private val preferences: SharedPreferences
) : ConfigurationsRepository {
    private val serverAvailabilityCache = ConcurrentHashMap<String, Pair<Boolean, Long>>()

    override fun checkHealth(listener: OnSuccessListener) {
        serviceScope.launch {
            val result = healthAccess()
            withContext(Dispatchers.Main) {
                listener.onSuccess(result)
            }
        }
    }

    override suspend fun healthAccess(): String {
        return withContext(Dispatchers.IO) {
            try {
                val healthUrl = UrlUtils.getHealthAccessUrl(preferences)
                if (healthUrl.isBlank()) {
                    return@withContext ""
                }

                try {
                    val response = apiInterface.healthAccess(healthUrl)
                    when (response.code()) {
                        200 -> context.getString(R.string.server_sync_successfully)
                        401 -> "Unauthorized - Invalid credentials"
                        404 -> "Server endpoint not found"
                        500 -> "Server internal error"
                        502 -> "Bad gateway - Server unavailable"
                        503 -> "Service temporarily unavailable"
                        504 -> "Gateway timeout"
                        else -> "Server error: ${response.code()}"
                    }
                } catch (t: Exception) {
                    t.printStackTrace()
                    when (t) {
                        is java.net.UnknownHostException -> "Server not reachable"
                        is java.net.SocketTimeoutException -> "Connection timeout"
                        is java.net.ConnectException -> "Unable to connect to server"
                        is java.io.IOException -> "Network connection error"
                        else -> "Network error: ${t.localizedMessage ?: "Unknown error"}"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                "Health access initialization failed"
            }
        }
    }

    override fun checkVersion(callback: ConfigurationsRepository.CheckVersionCallback, settings: SharedPreferences) {
        serviceScope.launch {
            val result = checkVersion(settings)
            withContext(Dispatchers.Main) {
                when (result) {
                    is ConfigurationsRepository.VersionCheckResult.UpdateAvailable -> {
                        callback.onUpdateAvailable(result.info, result.cancelable)
                    }
                    is ConfigurationsRepository.VersionCheckResult.Error -> {
                        callback.onError(result.msg, result.blockSync)
                    }
                    is ConfigurationsRepository.VersionCheckResult.UpToDate -> {
                        callback.onError(context.getString(R.string.planet_is_up_to_date), false)
                    }
                }
            }
        }
    }

    override suspend fun checkVersion(settings: SharedPreferences): ConfigurationsRepository.VersionCheckResult {
        val lastCheckTime = preferences.getLong("last_version_check_timestamp", 0)
        val currentTime = System.currentTimeMillis()
        val twentyFourHoursInMillis = 24 * 60 * 60 * 1000

        if (currentTime - lastCheckTime < twentyFourHoursInMillis) {
            val cachedVersionDetail = preferences.getString("versionDetail", null)
            val cachedApkVersion = preferences.getInt("cachedApkVersion", -1)

            if (cachedVersionDetail != null && cachedApkVersion != -1) {
                try {
                    val cachedInfo = JsonUtils.gson.fromJson(cachedVersionDetail, MyPlanet::class.java)
                    return evaluateVersion(cachedInfo, cachedApkVersion)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        try {
            val planetInfo = fetchVersionInfo(settings)
                ?: return ConfigurationsRepository.VersionCheckResult.Error(context.getString(R.string.version_not_found), true)

            preferences.edit {
                putLong("last_version_check_timestamp", System.currentTimeMillis())
                putInt("LastWifiID", NetworkUtils.getCurrentNetworkId(context))
                putString("versionDetail", JsonUtils.gson.toJson(planetInfo))
            }

            val rawApkVersion = fetchApkVersionString(settings)
            val versionStr = JsonUtils.gson.fromJson(rawApkVersion, String::class.java)
            if (versionStr.isNullOrEmpty()) {
                return ConfigurationsRepository.VersionCheckResult.UpToDate
            }

            val apkVersion = parseApkVersionString(versionStr)
                ?: return ConfigurationsRepository.VersionCheckResult.Error(
                    context.getString(R.string.new_apk_version_required_but_not_found_on_server),
                    false
                )

            preferences.edit {
                putInt("cachedApkVersion", apkVersion)
            }

            return evaluateVersion(planetInfo, apkVersion)
        } catch (e: Exception) {
            e.printStackTrace()
            return ConfigurationsRepository.VersionCheckResult.Error(context.getString(R.string.connection_failed), true)
        }
    }

    override fun checkServerAvailability(callback: ConfigurationsRepository.PlanetAvailableListener?) {
        val updateUrl = "${preferences.getString("serverURL", "")}"
        serverAvailabilityCache[updateUrl]?.let { (available, timestamp) ->
            if (System.currentTimeMillis() - timestamp < 30000) {
                if (available) {
                    callback?.isAvailable()
                } else {
                    callback?.notAvailable()
                }
                return
            }
        }

        val serverUrlMapper = ServerUrlMapper()
        val mapping = serverUrlMapper.processUrl(updateUrl)

        serviceScope.launch {
            withContext(Dispatchers.IO) {
                val primaryReachable = isServerReachable(mapping.primaryUrl)
                val alternativeReachable = mapping.alternativeUrl?.let { isServerReachable(it) } == true

                if (!primaryReachable && alternativeReachable) {
                    mapping.alternativeUrl?.let { alternativeUrl ->
                        val uri = updateUrl.toUri()
                        val editor = preferences.edit()

                        serverUrlMapper.updateUrlPreferences(
                            editor,
                            uri,
                            alternativeUrl,
                            mapping.primaryUrl,
                            preferences
                        )
                    }
                }
            }

            try {
                val response = apiInterface.isPlanetAvailable(UrlUtils.getUpdateUrl(preferences))
                val isAvailable = callback != null && response.code() == 200
                serverAvailabilityCache[updateUrl] = Pair(isAvailable, System.currentTimeMillis())
                withContext(Dispatchers.Main) {
                    if (isAvailable) {
                        callback.isAvailable()
                    } else {
                        callback?.notAvailable()
                    }
                }
            } catch (e: Exception) {
                serverAvailabilityCache[updateUrl] = Pair(false, System.currentTimeMillis())
                withContext(Dispatchers.Main) {
                    callback?.notAvailable()
                }
            }
        }
    }

    private suspend fun fetchVersionInfo(settings: SharedPreferences): MyPlanet? =
        withContext(Dispatchers.IO) {
            val result = ApiClient.executeWithResult {
                apiInterface.checkVersion(UrlUtils.getUpdateUrl(settings))
            }
            when (result) {
                is NetworkResult.Success -> result.data
                else -> null
            }
        }

    private suspend fun fetchApkVersionString(settings: SharedPreferences): String? =
        withContext(Dispatchers.IO) {
            val result = ApiClient.executeWithResult {
                apiInterface.getApkVersion(UrlUtils.getApkVersionUrl(settings))
            }
            when (result) {
                is NetworkResult.Success -> result.data.string()
                else -> null
            }
        }

    private fun parseApkVersionString(raw: String?): Int? {
        if (raw.isNullOrEmpty()) return null
        var vsn = raw.replace("v".toRegex(), "")
        vsn = vsn.replace("\\.".toRegex(), "")
        val cleaned = if (vsn.startsWith("0")) vsn.replaceFirst("0", "") else vsn
        return cleaned.toIntOrNull()
    }

    private fun evaluateVersion(info: MyPlanet, apkVersion: Int): ConfigurationsRepository.VersionCheckResult {
        val currentVersion = VersionUtils.getVersionCode(context)
        if (Constants.showBetaFeature(Constants.KEY_UPGRADE_MAX, context) && info.latestapkcode > currentVersion) {
            return ConfigurationsRepository.VersionCheckResult.UpdateAvailable(info, false)
        }
        if (apkVersion > currentVersion) {
            return ConfigurationsRepository.VersionCheckResult.UpdateAvailable(info, currentVersion >= info.minapkcode)
        }
        if (currentVersion < info.minapkcode && apkVersion < info.minapkcode) {
             return ConfigurationsRepository.VersionCheckResult.UpdateAvailable(info, true)
        } else {
             return ConfigurationsRepository.VersionCheckResult.UpToDate
        }
    }

    private suspend fun isServerReachable(url: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiInterface.isPlanetAvailable(url)
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }
    }
}
