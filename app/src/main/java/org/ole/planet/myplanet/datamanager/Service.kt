package org.ole.planet.myplanet.datamanager

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.net.Uri
import android.os.*
import android.text.TextUtils
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.realm.Realm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.model.RealmCommunity
import org.ole.planet.myplanet.model.RealmUserModel.Companion.isUserExists
import org.ole.planet.myplanet.model.RealmUserModel.Companion.populateUsersTable
import org.ole.planet.myplanet.service.TransactionSyncManager
import org.ole.planet.myplanet.service.UploadToShelfService
import org.ole.planet.myplanet.ui.sync.ProcessUserDataActivity
import org.ole.planet.myplanet.ui.sync.SyncActivity
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.generateIv
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.generateKey
import org.ole.planet.myplanet.utilities.Constants.KEY_UPGRADE_MAX
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.Constants.showBetaFeature
import org.ole.planet.myplanet.utilities.DialogUtils.CustomProgressDialog
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.NetworkUtils.extractProtocol
import org.ole.planet.myplanet.utilities.NetworkUtils.isNetworkConnectedFlow
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.Sha256Utils
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.VersionUtils
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.math.min

class Service(private val context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val retrofitInterface: ApiInterface? = ApiClient.client?.create(ApiInterface::class.java)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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
        if (!settings.getBoolean("isAlternativeUrl", false)){
            if (settings.getString("couchdbURL", "")?.isEmpty() == true) {
                callback.onError(context.getString(R.string.config_not_available), true)
                return
            }
        }

        retrofitInterface?.checkVersion(Utilities.getUpdateUrl(settings))?.enqueue(object : Callback<MyPlanet?> {
            override fun onResponse(call: Call<MyPlanet?>, response: Response<MyPlanet?>) {
                preferences.edit().putInt("LastWifiID", NetworkUtils.getCurrentNetworkId(context)).apply()
                if (response.body() != null) {
                    val p = response.body()
                    preferences.edit().putString("versionDetail", Gson().toJson(response.body())).apply()
                    retrofitInterface.getApkVersion(Utilities.getApkVersionUrl(settings)).enqueue(object : Callback<ResponseBody> {
                        override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                            val responses: String?
                            try {
                                responses = Gson().fromJson(response.body()?.string(), String::class.java)
                                if (responses == null || responses.isEmpty()) {
                                    callback.onError(context.getString(R.string.planet_is_up_to_date), false)
                                    return
                                }
                                var vsn = responses.replace("v".toRegex(), "")
                                vsn = vsn.replace("\\.".toRegex(), "")
                                val apkVersion = (if (vsn.startsWith("0")) vsn.replace("0", "") else vsn).toInt()
                                val currentVersion = VersionUtils.getVersionCode(context)
                                if (p != null) {
                                    if (showBetaFeature(KEY_UPGRADE_MAX, context) && p.latestapkcode > currentVersion) {
                                        callback.onUpdateAvailable(p, false)
                                        return
                                    }
                                }
                                if (apkVersion > currentVersion) {
                                    if (p != null) {
                                        callback.onUpdateAvailable(p, currentVersion >= p.minapkcode)
                                    }
                                    return
                                }
                                if (p != null) {
                                    if (currentVersion < p.minapkcode && apkVersion < p.minapkcode) {
                                        callback.onUpdateAvailable(p, true)
                                    } else {
                                        callback.onError(context.getString(R.string.planet_is_up_to_date), false)
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                callback.onError(context.getString(R.string.new_apk_version_required_but_not_found_on_server), false)
                            }
                        }
                        override fun onFailure(call: Call<ResponseBody>, t: Throwable) {}
                    })
                } else {
                    callback.onError(context.getString(R.string.version_not_found), true)
                }
            }

            override fun onFailure(call: Call<MyPlanet?>, t: Throwable) {
                t.printStackTrace()
                callback.onError(context.getString(R.string.connection_failed), true)
            }
        })
    }

    fun isPlanetAvailable(callback: PlanetAvailableListener?) {
        val updateUrl = "${preferences.getString("serverURL", "")}"
        val serverUrlMapper = ServerUrlMapper(context)
        val mapping = serverUrlMapper.processUrl(updateUrl)

        CoroutineScope(Dispatchers.IO).launch {
            val primaryAvailable = isServerReachable(mapping.primaryUrl)
            val alternativeAvailable = mapping.alternativeUrl?.let { isServerReachable(it) } == true

            if (!primaryAvailable && alternativeAvailable) {
                mapping.alternativeUrl.let { alternativeUrl ->
                    val uri = Uri.parse(updateUrl)
                    val editor = preferences.edit()

                    serverUrlMapper.updateUrlPreferences(editor, uri, alternativeUrl, mapping.primaryUrl, preferences)
                }
            }

            withContext(Dispatchers.Main) {
                retrofitInterface?.isPlanetAvailable(Utilities.getUpdateUrl(preferences))
                    ?.enqueue(object : Callback<ResponseBody?> {
                        override fun onResponse(call: Call<ResponseBody?>, response: Response<ResponseBody?>) {
                            if (callback != null && response.code() == 200) {
                                callback.isAvailable()
                            } else {
                                callback?.notAvailable()
                            }
                        }

                        override fun onFailure(call: Call<ResponseBody?>, t: Throwable) {
                            callback?.notAvailable()
                        }
                    })
            }
        }
    }

    fun becomeMember(realm: Realm, obj: JsonObject, callback: CreateUserCallback) {
        isPlanetAvailable(object : PlanetAvailableListener {
            override fun isAvailable() {
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

                retrofitInterface?.getJsonObject(Utilities.header, "${Utilities.getUrl()}/_users/org.couchdb.user:${obj["name"].asString}")?.enqueue(object : Callback<JsonObject> {
                    override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                        if (response.body() != null && response.body()?.has("_id") == true) {
                            callback.onSuccess(context.getString(R.string.unable_to_create_user_user_already_exists))
                        } else {
                            retrofitInterface.putDoc(null, "application/json", "${Utilities.getUrl()}/_users/org.couchdb.user:${obj["name"].asString}", obj).enqueue(object : Callback<JsonObject> {
                                override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                                    if (response.body() != null && response.body()!!.has("id")) {
                                        uploadToShelf(obj)
                                    } else {
                                        callback.onSuccess(context.getString(R.string.unable_to_create_user))
                                    }
                                }

                                override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                                    callback.onSuccess(context.getString(R.string.unable_to_create_user))
                                }
                            })
                        }
                    }

                    override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                        callback.onSuccess(context.getString(R.string.unable_to_create_user))
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
            }
        })
    }

    private fun uploadToShelf(obj: JsonObject) {
        retrofitInterface?.putDoc(null, "application/json", Utilities.getUrl() + "/shelf/org.couchdb.user:" + obj["name"].asString, JsonObject())?.enqueue(object : Callback<JsonObject?> {
            override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {}

            override fun onFailure(call: Call<JsonObject?>, t: Throwable) {}
        })
    }

    fun syncPlanetServers(callback: SuccessListener) {
        retrofitInterface?.getJsonObject("", "https://planet.earth.ole.org/db/communityregistrationrequests/_all_docs?include_docs=true")?.enqueue(object : Callback<JsonObject?> {
            override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                if (response.body() != null) {
                    val arr = JsonUtils.getJsonArray("rows", response.body())

                    Executors.newSingleThreadExecutor().execute {
                        val backgroundRealm = Realm.getDefaultInstance()
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

                            Handler(Looper.getMainLooper()).post {
                                callback.onSuccess(context.getString(R.string.server_sync_successfully))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            backgroundRealm.close()
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
        val serverUrlMapper = ServerUrlMapper(context)
        val mapping = serverUrlMapper.processUrl(url)
        val urlsToTry = mutableListOf(url).apply { mapping.alternativeUrl?.let { add(it) } }

        MainApplication.applicationScope.launch {
            val customProgressDialog = withContext(Dispatchers.Main) {
                CustomProgressDialog(context).apply {
                    setText(context.getString(R.string.check_apk_version))
                    show()
                }
            }

            try {
                val result = withContext(Dispatchers.IO) {
                    urlsToTry.map { currentUrl ->
                        async {
                            try {
                                val versionsResponse = retrofitInterface?.getConfiguration("$currentUrl/versions")?.execute()
                                if (versionsResponse?.isSuccessful == true) {
                                    val jsonObject = versionsResponse.body()
                                    val minApkVersion = jsonObject?.get("minapk")?.asString
                                    val currentVersion = context.getString(R.string.app_version)

                                    if (minApkVersion != null && isVersionAllowed(currentVersion, minApkVersion)) {
                                        val uri = Uri.parse(currentUrl)
                                        val couchdbURL = if (currentUrl.contains("@")) {
                                            getUserInfo(uri)
                                            currentUrl
                                        } else {
                                            val urlUser = "satellite"
                                            "${uri.scheme}://$urlUser:$pin@${uri.host}:${if (uri.port == -1) if (uri.scheme == "http") 80 else 443 else uri.port}"
                                        }

                                        withContext(Dispatchers.Main) {
                                            customProgressDialog.setText(context.getString(R.string.checking_server))
                                        }

                                        val configResponse = withContext(Dispatchers.IO) {
                                            retrofitInterface.getConfiguration("${getUrl(couchdbURL)}/configurations/_all_docs?include_docs=true").execute()
                                        }

                                        if (configResponse.isSuccessful) {
                                            val rows = configResponse.body()?.getAsJsonArray("rows")
                                            if (rows != null && rows.size() > 0) {
                                                val firstRow = rows[0].asJsonObject
                                                val id = firstRow.getAsJsonPrimitive("id").asString
                                                val doc = firstRow.getAsJsonObject("doc")
                                                val code = doc.getAsJsonPrimitive("code").asString
                                                val parentCode = doc.getAsJsonPrimitive("parentCode").asString

                                                withContext(Dispatchers.IO) {
                                                    preferences.edit().putString("parentCode", parentCode).apply()
                                                }

                                                if (doc.has("models")) {
                                                    val modelsMap = doc.getAsJsonObject("models").entrySet()
                                                        .associate { it.key to it.value.asString }

                                                    withContext(Dispatchers.IO) {
                                                        preferences.edit().putString("ai_models", Gson().toJson(modelsMap)).apply()
                                                    }
                                                }
                                                return@async UrlCheckResult.Success(id, code, currentUrl)
                                            }
                                        }
                                    }
                                }
                                return@async UrlCheckResult.Failure(currentUrl)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                return@async UrlCheckResult.Failure(currentUrl)
                            }
                        }
                    }.awaitFirst { it is UrlCheckResult.Success }
                }

                when (result) {
                    is UrlCheckResult.Success -> {
                        val isAlternativeUrl = result.url != url
                        listener?.onConfigurationIdReceived(result.id, result.code, result.url, url, isAlternativeUrl, callerActivity)
                        activity.setSyncFailed(false)
                    }
                    is UrlCheckResult.Failure -> {
                        activity.setSyncFailed(true)
                        val errorMessage = when (extractProtocol(url)) {
                            context.getString(R.string.http_protocol) -> context.getString(R.string.device_couldn_t_reach_local_server)
                            context.getString(R.string.https_protocol) -> context.getString(R.string.device_couldn_t_reach_nation_server)
                            else -> context.getString(R.string.device_couldn_t_reach_local_server)
                        }
                        showAlertDialog(errorMessage, false)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                activity.setSyncFailed(true)
                withContext(Dispatchers.Main) {
                    showAlertDialog(context.getString(R.string.device_couldn_t_reach_local_server), false)
                }
            } finally {
                customProgressDialog.dismiss()
            }
        }
    }

    sealed class UrlCheckResult {
        data class Success(val id: String, val code: String, val url: String) : UrlCheckResult()
        data class Failure(val url: String) : UrlCheckResult()
    }

    private suspend fun <T> List<Deferred<T>>.awaitFirst(predicate: (T) -> Boolean): T {
        return firstOrNull { job ->
            try {
                val result = job.await()
                predicate(result)
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }?.await() ?: throw NoSuchElementException("No matching result found")
    }

    private fun isVersionAllowed(currentVersion: String, minApkVersion: String): Boolean {
        return compareVersions(currentVersion, minApkVersion) >= 0
    }

    private fun compareVersions(version1: String, version2: String): Int {
        val parts1 = version1.removeSuffix("-lite").removePrefix("v").split(".").map { it.toInt() }
        val parts2 = version2.removePrefix("v").split(".").map { it.toInt() }

        for (i in 0 until min(parts1.size, parts2.size)) {
            if (parts1[i] != parts2[i]) {
                return parts1[i].compareTo(parts2[i])
            }
        }
        return parts1.size.compareTo(parts2.size)
    }

    fun showAlertDialog(message: String?, playStoreRedirect: Boolean) {
        MainApplication.applicationScope.launch(Dispatchers.Main) {
            val builder = AlertDialog.Builder(context, R.style.CustomAlertDialog)
            builder.setMessage(message)
            builder.setCancelable(true)
            builder.setNegativeButton(R.string.okay) { dialog: DialogInterface, _: Int ->
                if (playStoreRedirect) {
                    Utilities.openPlayStore()
                }
                dialog.cancel()
            }
            val alert = builder.create()
            alert.show()
        }
    }

    private fun getUrl(couchdbURL: String): String {
        var url = couchdbURL

        if (!url.endsWith("/db")) {
            url += "/db"
        }
        return url
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
