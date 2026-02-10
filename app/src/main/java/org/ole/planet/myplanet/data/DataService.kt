package org.ole.planet.myplanet.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.JsonObject
import dagger.hilt.android.EntryPointAccessors
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnSecurityDataListener
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.data.api.ApiClient
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.di.ApiInterfaceEntryPoint
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.di.ApplicationScopeEntryPoint
import org.ole.planet.myplanet.di.AutoSyncEntryPoint
import org.ole.planet.myplanet.di.DatabaseServiceEntryPoint
import org.ole.planet.myplanet.di.RepositoryEntryPoint
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.model.RealmCommunity
import org.ole.planet.myplanet.repository.CommunityRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.ConfigurationManager
import org.ole.planet.myplanet.services.UploadToShelfService
import org.ole.planet.myplanet.ui.sync.ProcessUserDataActivity
import org.ole.planet.myplanet.ui.sync.SyncActivity
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.Sha256Utils
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.Utilities
import org.ole.planet.myplanet.utils.VersionUtils

class DataService constructor(
    private val context: Context,
    private val retrofitInterface: ApiInterface,
    private val databaseService: DatabaseService,
    @param:ApplicationScope private val serviceScope: CoroutineScope,
    private val userRepository: UserRepository,
    private val uploadToShelfService: UploadToShelfService,
    private val communityRepository: CommunityRepository,
) {
    constructor(context: Context) : this(
        context,
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ApiInterfaceEntryPoint::class.java
        ).apiInterface(),
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            DatabaseServiceEntryPoint::class.java
        ).databaseService(),
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ApplicationScopeEntryPoint::class.java
        ).applicationScope(),
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            RepositoryEntryPoint::class.java
        ).userRepository(),
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AutoSyncEntryPoint::class.java
        ).uploadToShelfService(),
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            RepositoryEntryPoint::class.java
        ).communityRepository(),
    )

    private val preferences: SharedPreferences = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private val serverAvailabilityCache = ConcurrentHashMap<String, Pair<Boolean, Long>>()
    private val configurationManager =
        ConfigurationManager(context, preferences, retrofitInterface)

    @Deprecated("Use ConfigurationsRepository.checkHealth instead")
    fun healthAccess(listener: OnSuccessListener) {
        serviceScope.launch {
            try {
                val healthUrl = UrlUtils.getHealthAccessUrl(preferences)
                if (healthUrl.isBlank()) {
                    withContext(Dispatchers.Main) { listener.onSuccess("") }
                    return@launch
                }

                try {
                    val response = retrofitInterface.healthAccess(healthUrl)
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

    @Deprecated("Use ConfigurationsRepository.checkCheckSum instead")
    suspend fun checkCheckSum(path: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = retrofitInterface.getChecksum(UrlUtils.getChecksumUrl(preferences))
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

    @Deprecated("Use ConfigurationsRepository.checkVersion instead")
    fun checkVersion(callback: CheckVersionCallback, settings: SharedPreferences) {
        if (shouldPromptForSettings(settings)) return

        serviceScope.launch {
            withContext(Dispatchers.Main) {
                callback.onCheckingVersion()
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

                handleVersionEvaluation(planetInfo, apkVersion, callback)
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    callback.onError(context.getString(R.string.connection_failed), true)
                }
            }
        }
    }

    fun becomeMember(obj: JsonObject, callback: CreateUserCallback, securityCallback: OnSecurityDataListener? = null) {
        serviceScope.launch {
            val result = userRepository.becomeMember(obj)
            withContext(Dispatchers.Main) {
                if (result.first) {
                    if (context is ProcessUserDataActivity) {
                        val userName = obj["name"].asString
                        context.startUpload("becomeMember", userName, securityCallback)
                    }

                    if (result.second == context.getString(R.string.not_connect_to_planet_created_user_offline)) {
                        Utilities.toast(MainApplication.context, result.second)
                        securityCallback?.onSecurityDataUpdated()
                    }

                    callback.onSuccess(result.second)
                } else {
                    callback.onSuccess(result.second)
                    securityCallback?.onSecurityDataUpdated()
                }
            }
        }
    }

    suspend fun syncPlanetServers(callback: OnSuccessListener) {
        try {
            val response = withContext(Dispatchers.IO) {
                retrofitInterface.getJsonObject("", "https://planet.earth.ole.org/db/communityregistrationrequests/_all_docs?include_docs=true")
            }

            if (response.isSuccessful && response.body() != null) {
                val arr = JsonUtils.getJsonArray("rows", response.body())
                val startTime = System.currentTimeMillis()
                println("Realm transaction started")

                val transactionResult = runCatching {
                    communityRepository.replaceAll(arr)
                }

                val endTime = System.currentTimeMillis()
                println("Realm transaction finished in ${endTime - startTime}ms")

                withContext(Dispatchers.Main) {
                    transactionResult.onSuccess {
                        callback.onSuccess(context.getString(R.string.server_sync_successfully))
                    }.onFailure { e ->
                        e.printStackTrace()
                        callback.onSuccess(context.getString(R.string.server_sync_has_failed))
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    callback.onSuccess(context.getString(R.string.server_sync_has_failed))
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            withContext(Dispatchers.Main) {
                callback.onSuccess(context.getString(R.string.server_sync_has_failed))
            }
        }
    }

    fun getMinApk(listener: ConfigurationIdListener?, url: String, pin: String, activity: SyncActivity, callerActivity: String) {
        configurationManager.getMinApk(listener, url, pin, activity, callerActivity)
    }

    private fun shouldPromptForSettings(settings: SharedPreferences): Boolean {
        if (!settings.getBoolean("isAlternativeUrl", false)) {
            if (settings.getString("couchdbURL", "").isNullOrEmpty()) {
                (context as? SyncActivity)?.settingDialog()
                return true
            }
        }
        return false
    }

    private suspend fun fetchVersionInfo(settings: SharedPreferences): MyPlanet? =
        withContext(Dispatchers.IO) {
            val result = ApiClient.executeWithResult {
                retrofitInterface.checkVersion(UrlUtils.getUpdateUrl(settings))
            }
            when (result) {
                is NetworkResult.Success -> result.data
                else -> null
            }
        }

    private suspend fun fetchApkVersionString(settings: SharedPreferences): String? =
        withContext(Dispatchers.IO) {
            val result = ApiClient.executeWithResult {
                retrofitInterface.getApkVersion(UrlUtils.getApkVersionUrl(settings))
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

    private fun handleVersionEvaluation(info: MyPlanet, apkVersion: Int, callback: CheckVersionCallback) {
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

    interface CheckVersionCallback {
        fun onUpdateAvailable(info: MyPlanet?, cancelable: Boolean)
        fun onCheckingVersion()
        fun onError(msg: String, blockSync: Boolean)
    }

    interface CreateUserCallback {
        fun onSuccess(message: String)
    }

    interface ConfigurationIdListener {
        fun onConfigurationIdReceived(id: String, code: String, url: String, defaultUrl: String, isAlternativeUrl: Boolean, callerActivity: String)
    }
}
