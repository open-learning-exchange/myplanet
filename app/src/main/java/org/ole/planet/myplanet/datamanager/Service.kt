package org.ole.planet.myplanet.datamanager

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.text.TextUtils
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.realm.Realm
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.SecurityDataCallback
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.datamanager.ConfigurationManager
import org.ole.planet.myplanet.datamanager.NetworkResult
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.model.RealmCommunity
import org.ole.planet.myplanet.model.RealmUserModel.Companion.isUserExists
import org.ole.planet.myplanet.model.RealmUserModel.Companion.populateUsersTable
import org.ole.planet.myplanet.service.UploadToShelfService
import org.ole.planet.myplanet.ui.sync.ProcessUserDataActivity
import org.ole.planet.myplanet.ui.sync.SyncActivity
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.generateIv
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.generateKey
import org.ole.planet.myplanet.utilities.Constants.KEY_UPGRADE_MAX
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.Constants.showBetaFeature
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.LocaleHelper
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.NetworkUtils.isNetworkConnectedFlow
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.Sha256Utils
import org.ole.planet.myplanet.utilities.UrlUtils
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.VersionUtils
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class Service(private val context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val retrofitInterface: ApiInterface? = ApiClient.client?.create(ApiInterface::class.java)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val serverAvailabilityCache = ConcurrentHashMap<String, Pair<Boolean, Long>>()
    private val configurationManager = ConfigurationManager(context, preferences, retrofitInterface)

    fun healthAccess(listener: SuccessListener) {
        retrofitInterface?.healthAccess(Utilities.getHealthAccessUrl(preferences))?.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.code() == 200) {
                    listener.onSuccess(context.getString(R.string.server_sync_successfully))
                } else {
                    listener.onSuccess("")
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                listener.onSuccess("")
            }
        })
    }

    fun checkCheckSum(callback: ChecksumCallback, path: String?) {
        retrofitInterface?.getChecksum(Utilities.getChecksumUrl(preferences))?.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.code() == 200) {
                    try {
                        val checksum = response.body()?.string()
                        if (TextUtils.isEmpty(checksum)) {
                            val f = FileUtils.getSDPathFromUrl(path)
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
            callback.onCheckingVersion()
            try {
                val planetInfo = fetchVersionInfo(settings)
                if (planetInfo == null) {
                    callback.onError(context.getString(R.string.version_not_found), true)
                    return@launch
                }

                preferences.edit {
                    putInt("LastWifiID", NetworkUtils.getCurrentNetworkId(context))
                    putString("versionDetail", Gson().toJson(planetInfo))
                }

                val rawApkVersion = fetchApkVersionString(settings)
                val versionStr = Gson().fromJson(rawApkVersion, String::class.java)
                if (versionStr.isNullOrEmpty()) {
                    callback.onError(context.getString(R.string.planet_is_up_to_date), false)
                    return@launch
                }

                val apkVersion = parseApkVersionString(versionStr)
                    ?: run {
                        callback.onError(
                            context.getString(R.string.new_apk_version_required_but_not_found_on_server),
                            false
                        )
                        return@launch
                    }

                handleVersionEvaluation(planetInfo, apkVersion, callback)
            } catch (e: Exception) {
                e.printStackTrace()
                callback.onError(context.getString(R.string.connection_failed), true)
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

        CoroutineScope(Dispatchers.IO).launch {
            val primaryAvailable = isServerReachable(mapping.primaryUrl)
            val alternativeAvailable = mapping.alternativeUrl?.let { isServerReachable(it) } == true

            if (!primaryAvailable && alternativeAvailable) {
                mapping.alternativeUrl.let { alternativeUrl ->
                    val uri = updateUrl.toUri()
                    val editor = preferences.edit()

                    serverUrlMapper.updateUrlPreferences(editor, uri, alternativeUrl, mapping.primaryUrl, preferences)
                }
            }

            withContext(Dispatchers.Main) {
                retrofitInterface?.isPlanetAvailable(Utilities.getUpdateUrl(preferences))?.enqueue(object : Callback<ResponseBody?> {
                    override fun onResponse(call: Call<ResponseBody?>, response: Response<ResponseBody?>) {
                        val isAvailable = callback != null && response.code() == 200
                        serverAvailabilityCache[updateUrl] = Pair(isAvailable, System.currentTimeMillis())
                        if (isAvailable) {
                            callback.isAvailable()
                        } else {
                            callback?.notAvailable()
                        }
                    }

                    override fun onFailure(call: Call<ResponseBody?>, t: Throwable) {
                        serverAvailabilityCache[updateUrl] = Pair(false, System.currentTimeMillis())
                        callback?.notAvailable()
                    }
                })
            }
        }
    }

    fun becomeMember(realm: Realm, obj: JsonObject, callback: CreateUserCallback, securityCallback: SecurityDataCallback? = null) {
        isPlanetAvailable(object : PlanetAvailableListener {
            override fun isAvailable() {
                retrofitInterface?.getJsonObject(Utilities.header, "${Utilities.getUrl()}/_users/org.couchdb.user:${obj["name"].asString}")?.enqueue(object : Callback<JsonObject> {
                    override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                        if (response.body() != null && response.body()?.has("_id") == true) {
                            callback.onSuccess(context.getString(R.string.unable_to_create_user_user_already_exists))
                        } else {
                            retrofitInterface.putDoc(null, "application/json", "${Utilities.getUrl()}/_users/org.couchdb.user:${obj["name"].asString}", obj).enqueue(object : Callback<JsonObject> {
                                override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                                    if (response.body() != null && response.body()?.has("id") == true) {
                                        uploadToShelf(obj)
                                        saveUserToDb(realm, "${response.body()?.get("id")?.asString}", obj, callback, securityCallback)
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
                val settings = MainApplication.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                if (isUserExists(realm, obj["name"].asString)) {
                    callback.onSuccess(context.getString(R.string.unable_to_create_user_user_already_exists))
                    return
                }
                realm.beginTransaction()
                val model = populateUsersTable(obj, realm, settings)
                val keyString = generateKey()
                val iv = generateIv()
                if (model != null) {
                    model.key = keyString
                    model.iv = iv
                }
                realm.commitTransaction()
                Utilities.toast(MainApplication.context, context.getString(R.string.not_connect_to_planet_created_user_offline))
                callback.onSuccess(context.getString(R.string.not_connect_to_planet_created_user_offline))
                securityCallback?.onSecurityDataUpdated()
            }
        })
    }

    private fun uploadToShelf(obj: JsonObject) {
        retrofitInterface?.putDoc(null, "application/json", Utilities.getUrl() + "/shelf/org.couchdb.user:" + obj["name"].asString, JsonObject())?.enqueue(object : Callback<JsonObject?> {
            override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {}

            override fun onFailure(call: Call<JsonObject?>, t: Throwable) {}
        })
    }

    private fun saveUserToDb(realm: Realm, id: String, obj: JsonObject, callback: CreateUserCallback, securityCallback: SecurityDataCallback? = null) {
        val settings = MainApplication.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        realm.executeTransactionAsync({ realm1: Realm? ->
            try {
                val res = retrofitInterface?.getJsonObject(Utilities.header, "${Utilities.getUrl()}/_users/$id")?.execute()
                if (res?.body() != null) {
                    val model = populateUsersTable(res.body(), realm1, settings)
                    if (model != null) {
                        UploadToShelfService(MainApplication.context).saveKeyIv(retrofitInterface, model, obj)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, {
            callback.onSuccess(context.getString(R.string.user_created_successfully))
            if (context is ProcessUserDataActivity) {
                context.runOnUiThread {
                    val userName = "${obj["name"].asString}"
                    context.startUpload("becomeMember", userName, securityCallback)
                }
            }
        }) { error: Throwable ->
            error.printStackTrace()
            callback.onSuccess(context.getString(R.string.unable_to_save_user_please_sync))
            securityCallback?.onSecurityDataUpdated()
        }
    }

    fun syncPlanetServers(callback: SuccessListener) {
        retrofitInterface?.getJsonObject("", "https://planet.earth.ole.org/db/communityregistrationrequests/_all_docs?include_docs=true")?.enqueue(object : Callback<JsonObject?> {
            override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                if (response.body() != null) {
                    val arr = JsonUtils.getJsonArray("rows", response.body())

                    Executors.newSingleThreadExecutor().execute {
                        Realm.getDefaultInstance().use { backgroundRealm ->
                            try {
                                backgroundRealm.executeTransaction { realm1 ->
                                    realm1.delete(RealmCommunity::class.java)
                                    for (j in arr) {
                                        var jsonDoc = j.asJsonObject
                                        jsonDoc = JsonUtils.getJsonObject("doc", jsonDoc)
                                        val id = JsonUtils.getString("_id", jsonDoc)
                                        val community = realm1.createObject(RealmCommunity::class.java, id)
                                        if (JsonUtils.getString("name", jsonDoc) == "learning") {
                                            community.weight = 0
                                        }
                                        community.localDomain = JsonUtils.getString("localDomain", jsonDoc)
                                        community.name = JsonUtils.getString("name", jsonDoc)
                                        community.parentDomain = JsonUtils.getString("parentDomain", jsonDoc)
                                        community.registrationRequest = JsonUtils.getString("registrationRequest", jsonDoc)
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }

            override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                callback.onSuccess(context.getString(R.string.server_sync_has_failed))
            }
        })
    }

    fun getMinApk(listener: ConfigurationIdListener?, url: String, pin: String, activity: SyncActivity, callerActivity: String) {
        configurationManager.getMinApk(listener, url, pin, activity, callerActivity)
    }

    fun showAlertDialog(message: String?, playStoreRedirect: Boolean) {
        configurationManager.showAlertDialog(message, playStoreRedirect)
    }

    private fun getUrl(couchdbURL: String): String {
        return UrlUtils.dbUrl(couchdbURL)
    }

    private fun getUserInfo(uri: Uri): Array<String> {
        val ar = arrayOf("", "")
        val info = uri.userInfo?.split(":".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()
        if ((info?.size ?: 0) > 1) {
            ar[0] = "${info?.get(0)}"
            ar[1] = "${info?.get(1)}"
        }
        return ar
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
                retrofitInterface?.checkVersion(Utilities.getUpdateUrl(settings))
            }
            when (result) {
                is NetworkResult.Success -> result.data
                else -> null
            }
        }

    private suspend fun fetchApkVersionString(settings: SharedPreferences): String? =
        withContext(Dispatchers.IO) {
            val result = ApiClient.executeWithResult {
                retrofitInterface?.getApkVersion(Utilities.getApkVersionUrl(settings))
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
        if (showBetaFeature(KEY_UPGRADE_MAX, context) && info.latestapkcode > currentVersion) {
            callback.onUpdateAvailable(info, false)
            return
        }
        if (apkVersion > currentVersion) {
            callback.onUpdateAvailable(info, currentVersion >= info.minapkcode)
            return
        }
        if (currentVersion < info.minapkcode && apkVersion < info.minapkcode) {
            callback.onUpdateAvailable(info, true)
        } else {
            callback.onError(context.getString(R.string.planet_is_up_to_date), false)
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
