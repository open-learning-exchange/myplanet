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
import okhttp3.ResponseBody
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.data.ApiClient
import org.ole.planet.myplanet.data.ApiInterface
import org.ole.planet.myplanet.data.NetworkResult
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.service.sync.ServerUrlMapper
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.UrlUtils
import org.ole.planet.myplanet.utilities.VersionUtils
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

import org.ole.planet.myplanet.di.AppPreferences

class ConfigurationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiInterface: ApiInterface,
    @ApplicationScope private val serviceScope: CoroutineScope,
    @AppPreferences private val preferences: SharedPreferences
) : ConfigurationRepository {
    private val serverAvailabilityCache = ConcurrentHashMap<String, Pair<Boolean, Long>>()

    override fun checkHealth(listener: SuccessListener) {
        try {
            val healthUrl = UrlUtils.getHealthAccessUrl(preferences)
            if (healthUrl.isBlank()) {
                listener.onSuccess("")
                return
            }

            apiInterface.healthAccess(healthUrl).enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    try {
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
                    } catch (e: Exception) {
                        e.printStackTrace()
                        listener.onSuccess("")
                    }
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    try {
                        t.printStackTrace()
                        val errorMsg = when (t) {
                            is java.net.UnknownHostException -> "Server not reachable"
                            is java.net.SocketTimeoutException -> "Connection timeout"
                            is java.net.ConnectException -> "Unable to connect to server"
                            is java.io.IOException -> "Network connection error"
                            else -> "Network error: ${t.localizedMessage ?: "Unknown error"}"
                        }
                        listener.onSuccess(errorMsg)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        listener.onSuccess("Health check failed")
                    }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
            listener.onSuccess("Health access initialization failed")
        }
    }

    override fun checkVersion(callback: ConfigurationRepository.CheckVersionCallback, settings: SharedPreferences) {
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

    override fun checkServerAvailability(callback: ConfigurationRepository.PlanetAvailableListener?) {
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

            apiInterface.isPlanetAvailable(UrlUtils.getUpdateUrl(preferences)).enqueue(object : Callback<ResponseBody?> {
                override fun onResponse(call: Call<ResponseBody?>, response: Response<ResponseBody?>) {
                    val isAvailable = callback != null && response.code() == 200
                    serverAvailabilityCache[updateUrl] = Pair(isAvailable, System.currentTimeMillis())
                    serviceScope.launch {
                        withContext(Dispatchers.Main) {
                            if (isAvailable) {
                                callback.isAvailable()
                            } else {
                                callback?.notAvailable()
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<ResponseBody?>, t: Throwable) {
                    serverAvailabilityCache[updateUrl] = Pair(false, System.currentTimeMillis())
                    serviceScope.launch {
                        withContext(Dispatchers.Main) {
                            callback?.notAvailable()
                        }
                    }
                }
            })
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

    private fun handleVersionEvaluation(info: MyPlanet, apkVersion: Int, callback: ConfigurationRepository.CheckVersionCallback) {
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
                val response = apiInterface.isPlanetAvailable(url).execute()
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }
    }
}
