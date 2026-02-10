package org.ole.planet.myplanet.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
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
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.Sha256Utils
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
            try {
                val healthUrl = UrlUtils.getHealthAccessUrl(preferences)
                if (healthUrl.isBlank()) {
                    withContext(Dispatchers.Main) { listener.onSuccess("") }
                    return@launch
                }

                try {
                    val response = apiInterface.healthAccess(healthUrl)
                    withContext(Dispatchers.Main) {
                        when (response.code()) {
                            200 -> listener.onSuccess(context.getString(R.string.server_sync_successfully))
                            401 -> listener.onSuccess("Unauthorized - Invalid credentials")
                            404 -> listener.onSuccess("Server endpoint not found")
                            500 -> listener.onSuccess("Server internal error")
                            502 -> listener.onSuccess("Bad gateway - Server unavailable")
                            503 -> listener.onSuccess("Service temporarily unavailable")
                            504 -> listener.onSuccess("Gateway timeout")
                            else -> listener.onSuccess("Server error: ${response.code()}")
                        }
                    }
                } catch (t: Exception) {
                    t.printStackTrace()
                    val errorMsg = when (t) {
                        is java.net.UnknownHostException -> "Server not reachable"
                        is java.net.SocketTimeoutException -> "Connection timeout"
                        is java.net.ConnectException -> "Unable to connect to server"
                        is java.io.IOException -> "Network connection error"
                        else -> "Network error: ${t.localizedMessage ?: "Unknown error"}"
                    }
                    withContext(Dispatchers.Main) { listener.onSuccess(errorMsg) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { listener.onSuccess("Health access initialization failed") }
            }
        }
    }

    override fun checkVersion(callback: ConfigurationsRepository.CheckVersionCallback, settings: SharedPreferences) {
        val baseUrl = UrlUtils.baseUrl(settings)
        if (baseUrl.isEmpty()) {
            return
        }

        serviceScope.launch {
            val lastCheckTime = preferences.getLong("last_version_check_timestamp", 0)
            val currentTime = System.currentTimeMillis()
            val twentyFourHoursInMillis = 24 * 60 * 60 * 1000

            if (currentTime - lastCheckTime < twentyFourHoursInMillis) {
                val cachedVersionDetail = preferences.getString("versionDetail", null)
                val cachedApkVersion = preferences.getInt("cachedApkVersion", -1)

                if (cachedVersionDetail != null && cachedApkVersion != -1) {
                    try {
                        val cachedInfo = JsonUtils.gson.fromJson(cachedVersionDetail, MyPlanet::class.java)
                        handleVersionEvaluation(cachedInfo, cachedApkVersion, callback)
                        return@launch
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            try {
                val planetInfo = fetchVersionInfo(settings)
                if (planetInfo == null) {
                    withContext(Dispatchers.Main) {
                        callback.onError(context.getString(R.string.version_not_found), true)
                    }
                    return@launch
                }

                preferences.edit {
                    putLong("last_version_check_timestamp", System.currentTimeMillis())
                    putInt("LastWifiID", NetworkUtils.getCurrentNetworkId(context))
                    putString("versionDetail", JsonUtils.gson.toJson(planetInfo))
                }

                val rawApkVersion = fetchApkVersionString(settings)
                val versionStr = JsonUtils.gson.fromJson(rawApkVersion, String::class.java)
                if (versionStr.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        callback.onError(context.getString(R.string.planet_is_up_to_date), false)
                    }
                    return@launch
                }

                val apkVersion = parseApkVersionString(versionStr)
                    ?: run {
                        withContext(Dispatchers.Main) {
                            callback.onError(
                                context.getString(R.string.new_apk_version_required_but_not_found_on_server),
                                false
                            )
                        }
                        return@launch
                    }

                preferences.edit {
                    putInt("cachedApkVersion", apkVersion)
                }

                handleVersionEvaluation(planetInfo, apkVersion, callback)
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    callback.onError(context.getString(R.string.connection_failed), true)
                }
            }
        }
    }

    override suspend fun isPlanetAvailable(): Boolean {
        val updateUrl = "${preferences.getString("serverURL", "")}"
        serverAvailabilityCache[updateUrl]?.let { (available, timestamp) ->
            if (System.currentTimeMillis() - timestamp < 30000) {
                return available
            }
        }

        val serverUrlMapper = ServerUrlMapper()
        val mapping = serverUrlMapper.processUrl(updateUrl)

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

        return try {
            val response = apiInterface.isPlanetAvailable(UrlUtils.getUpdateUrl(preferences))
            val isAvailable = response.code() == 200
            serverAvailabilityCache[updateUrl] = Pair(isAvailable, System.currentTimeMillis())
            isAvailable
        } catch (e: Exception) {
            serverAvailabilityCache[updateUrl] = Pair(false, System.currentTimeMillis())
            false
        }
    }

    override suspend fun checkCheckSum(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = apiInterface.getChecksum(UrlUtils.getChecksumUrl(preferences))
            if (response.isSuccessful) {
                val checksum = response.body()?.string()
                if (!checksum.isNullOrEmpty()) {
                    val f = FileUtils.getSDPathFromUrl(context, path)
                    if (f.exists()) {
                        val sha256 = Sha256Utils().getCheckSumFromFile(f)
                        return@withContext checksum.contains(sha256)
                    }
                }
            }
            false
        } catch (e: IOException) {
            e.printStackTrace()
            false
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

    private fun handleVersionEvaluation(info: MyPlanet, apkVersion: Int, callback: ConfigurationsRepository.CheckVersionCallback) {
        val currentVersion = VersionUtils.getVersionCode(context)
        if (Constants.showBetaFeature(Constants.KEY_UPGRADE_MAX, context) && info.latestapkcode > currentVersion) {
            serviceScope.launch {
                withContext(Dispatchers.Main) {
                    callback.onUpdateAvailable(info, false)
                }
            }
            return
        }
        if (apkVersion > currentVersion) {
            serviceScope.launch {
                withContext(Dispatchers.Main) {
                    callback.onUpdateAvailable(info, currentVersion >= info.minapkcode)
                }
            }
            return
        }
        if (currentVersion < info.minapkcode && apkVersion < info.minapkcode) {
            serviceScope.launch {
                withContext(Dispatchers.Main) {
                    callback.onUpdateAvailable(info, true)
                }
            }
        } else {
            serviceScope.launch {
                withContext(Dispatchers.Main) {
                    callback.onError(context.getString(R.string.planet_is_up_to_date), false)
                }
            }
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
