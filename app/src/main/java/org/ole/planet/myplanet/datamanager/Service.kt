package org.ole.planet.myplanet.datamanager

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.gson.JsonObject
import dagger.hilt.android.EntryPointAccessors
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.withTimeout
import okhttp3.ResponseBody
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.SecurityDataCallback
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.di.ApiInterfaceEntryPoint
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.di.ApplicationScopeEntryPoint
import org.ole.planet.myplanet.di.AutoSyncEntryPoint
import org.ole.planet.myplanet.di.RepositoryEntryPoint
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.model.RealmCommunity
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.service.UploadToShelfService
import org.ole.planet.myplanet.ui.sync.ProcessUserDataActivity
import org.ole.planet.myplanet.ui.sync.SyncActivity
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.GsonUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.Sha256Utils
import org.ole.planet.myplanet.utilities.UrlUtils
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.VersionUtils
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class Service @Inject constructor(
    private val context: Context,
    private val retrofitInterface: ApiInterface,
    @ApplicationScope private val serviceScope: CoroutineScope,
    private val userRepository: UserRepository,
) {
    constructor(context: Context) : this(
        context,
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ApiInterfaceEntryPoint::class.java
        ).apiInterface(),
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ApplicationScopeEntryPoint::class.java
        ).applicationScope(),
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            RepositoryEntryPoint::class.java
        ).userRepository(),
    )

    private val preferences: SharedPreferences = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private val serverAvailabilityCache = ConcurrentHashMap<String, Pair<Boolean, Long>>()
    private val configurationManager =
        ConfigurationManager(context, preferences, retrofitInterface)
    private fun getUploadToShelfService(): UploadToShelfService {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AutoSyncEntryPoint::class.java
        )
        return entryPoint.uploadToShelfService()
    }

    fun healthAccess(listener: SuccessListener) {
        try {
            val healthUrl = UrlUtils.getHealthAccessUrl(preferences)
            if (healthUrl.isBlank()) {
                listener.onSuccess("")
                return
            }

            retrofitInterface.healthAccess(healthUrl).enqueue(object : Callback<ResponseBody> {
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

    fun checkCheckSum(callback: ChecksumCallback, path: String?) {
        retrofitInterface.getChecksum(UrlUtils.getChecksumUrl(preferences)).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.code() == 200) {
                    try {
                        val checksum = response.body()?.string()
                        if (TextUtils.isEmpty(checksum)) {
                            val f = FileUtils.getSDPathFromUrl(context, path)
                            if (f.exists()) {
                                val sha256 = Sha256Utils().getCheckSumFromFile(f)
                                if (checksum?.contains(sha256) == true) {
                                    callback.onMatch()
                                    return
                                }
                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                callback.onFail()
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                callback.onFail()
            }
        })
    }

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
                    putString("versionDetail", GsonUtils.gson.toJson(planetInfo))
                }

                val rawApkVersion = fetchApkVersionString(settings)
                val versionStr = GsonUtils.gson.fromJson(rawApkVersion, String::class.java)
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

    fun isPlanetAvailable(callback: PlanetAvailableListener?) {
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

            retrofitInterface.isPlanetAvailable(UrlUtils.getUpdateUrl(preferences)).enqueue(object : Callback<ResponseBody?> {
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

    fun becomeMember(obj: JsonObject, callback: CreateUserCallback, securityCallback: SecurityDataCallback? = null) {
        isPlanetAvailable(object : PlanetAvailableListener {
            override fun isAvailable() {
                retrofitInterface.getJsonObject(UrlUtils.header, "${UrlUtils.getUrl()}/_users/org.couchdb.user:${obj["name"].asString}").enqueue(object : Callback<JsonObject> {
                     override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                         if (response.body() != null && response.body()?.has("_id") == true) {
                             callback.onSuccess(context.getString(R.string.unable_to_create_user_user_already_exists))
                         } else {
                             retrofitInterface.putDoc(null, "application/json", "${UrlUtils.getUrl()}/_users/org.couchdb.user:${obj["name"].asString}", obj).enqueue(object : Callback<JsonObject> {
                                 override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                                     if (response.body() != null && response.body()?.has("id") == true) {
                                         uploadToShelf(obj)
                                         serviceScope.launch {
                                             val result = saveUserToDb(
                                                 "${response.body()?.get("id")?.asString}",
                                                 obj,
                                                 securityCallback
                                             )
                                             withContext(Dispatchers.Main) {
                                                 if (result.isSuccess) {
                                                     callback.onSuccess(context.getString(R.string.user_created_successfully))
                                                 } else {
                                                     callback.onSuccess(context.getString(R.string.unable_to_save_user_please_sync))
                                                 }
                                             }
                                         }
                                     } else {
                                         callback.onSuccess(context.getString(R.string.unable_to_create_user_user_already_exists))
                                     }
                                 }

                                 override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                                     callback.onSuccess(context.getString(R.string.unable_to_create_user_user_already_exists))
                                 }
                             })
                         }
                     }

                    override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                        callback.onSuccess(context.getString(R.string.unable_to_create_user_user_already_exists))
                    }
                })
            }

            override fun notAvailable() {
                val settings = MainApplication.context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                serviceScope.launch {
                    val existingUser = userRepository.getUserByName(obj["name"].asString)
                    if (existingUser != null && existingUser._id?.startsWith("guest") != true) {
                        withContext(Dispatchers.Main) {
                            callback.onSuccess(context.getString(R.string.unable_to_create_user_user_already_exists))
                        }
                        return@launch
                    }

                    val keyString = AndroidDecrypter.generateKey()
                    val iv = AndroidDecrypter.generateIv()
                    userRepository.saveUser(obj, settings, keyString, iv)
                    withContext(Dispatchers.Main) {
                        Utilities.toast(MainApplication.context, context.getString(R.string.not_connect_to_planet_created_user_offline))
                        callback.onSuccess(context.getString(R.string.not_connect_to_planet_created_user_offline))
                        securityCallback?.onSecurityDataUpdated()
                    }
                }
            }
        })
    }

    private fun uploadToShelf(obj: JsonObject) {
        retrofitInterface.putDoc(null, "application/json", UrlUtils.getUrl() + "/shelf/org.couchdb.user:" + obj["name"].asString, JsonObject()).enqueue(object : Callback<JsonObject?> {
            override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {}
            override fun onFailure(call: Call<JsonObject?>, t: Throwable) {}
        })
    }

    private suspend fun saveUserToDb(id: String, obj: JsonObject, securityCallback: SecurityDataCallback? = null): Result<RealmUserModel?> {
        val settings = MainApplication.context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        return try {
            val userModel = withTimeout(20000) {
                val response = retrofitInterface.getJsonObjectSuspended(
                    UrlUtils.header,
                    "${UrlUtils.getUrl()}/_users/$id"
                )

                ensureActive()

                if (response.isSuccessful) {
                    response.body()?.let { userRepository.saveUser(it, settings) }
                } else {
                    null
                }
            }

            if (userModel != null) {
                getUploadToShelfService().saveKeyIv(retrofitInterface, userModel, obj)
                withContext(Dispatchers.Main) {
                    if (context is ProcessUserDataActivity) {
                        val userName = obj["name"].asString
                        context.startUpload("becomeMember", userName, securityCallback)
                    }
                }
                Result.success(userModel)
            } else {
                Result.failure(Exception("Failed to save user or user model was null"))
            }
        } catch (e: TimeoutCancellationException) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                securityCallback?.onSecurityDataUpdated()
            }
            Result.failure(e)
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                securityCallback?.onSecurityDataUpdated()
            }
            Result.failure(e)
        }
    }

    suspend fun syncPlanetServers(callback: SuccessListener) {
        try {
            val response = withContext(Dispatchers.IO) {
                retrofitInterface.getJsonObject(
                    "",
                    "https://planet.earth.ole.org/db/communityregistrationrequests/_all_docs?include_docs=true"
                ).execute()
            }

            if (response.isSuccessful && response.body() != null) {
                val arr = JsonUtils.getJsonArray("rows", response.body())
                val chunks = arr.chunked(100)
                val totalStartTime = System.currentTimeMillis()
                println("Realm processing started")

                val transactionResult = runCatching {
                    MainApplication.service.executeTransactionAsync {
                        it.delete(RealmCommunity::class.java)
                    }

                    for ((index, chunk) in chunks.withIndex()) {
                        yield()
                        val communities = chunk.map { j ->
                            var jsonDoc = j.asJsonObject
                            jsonDoc = JsonUtils.getJsonObject("doc", jsonDoc)
                            val community = JsonObject()
                            community.addProperty("id", JsonUtils.getString("_id", jsonDoc))
                            if (JsonUtils.getString("name", jsonDoc) == "learning") {
                                community.addProperty("weight", 0)
                            }
                            community.addProperty("localDomain", JsonUtils.getString("localDomain", jsonDoc))
                            community.addProperty("name", JsonUtils.getString("name", jsonDoc))
                            community.addProperty("parentDomain", JsonUtils.getString("parentDomain", jsonDoc))
                            community.addProperty("registrationRequest", JsonUtils.getString("registrationRequest", jsonDoc))
                            community
                        }
                        val chunkStartTime = System.currentTimeMillis()
                        MainApplication.service.executeTransactionAsync { realm1 ->
                            realm1.createOrUpdateAllFromJson(RealmCommunity::class.java, GsonUtils.gson.toJson(communities))
                        }
                        val chunkEndTime = System.currentTimeMillis()
                        println("Chunk ${index + 1} processed in ${chunkEndTime - chunkStartTime}ms")
                    }
                }

                val totalEndTime = System.currentTimeMillis()
                println("Realm processing finished in ${totalEndTime - totalStartTime}ms")

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

    interface ChecksumCallback {
        fun onMatch()
        fun onFail()
    }

    interface PlanetAvailableListener {
        fun isAvailable()
        fun notAvailable()
    }

    interface ConfigurationIdListener {
        fun onConfigurationIdReceived(id: String, code: String, url: String, defaultUrl: String, isAlternativeUrl: Boolean, callerActivity: String)
    }
}
