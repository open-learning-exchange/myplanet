package org.ole.planet.myplanet.datamanager

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.net.Uri
import android.os.*
import android.text.TextUtils
import android.util.Log
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
import androidx.core.net.toUri
import androidx.core.content.edit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.ole.planet.myplanet.model.RealmUserModel

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
                preferences.edit {
                    putInt("LastWifiID", NetworkUtils.getCurrentNetworkId(context))
                }
                if (response.body() != null) {
                    val p = response.body()
                    preferences.edit {
                        putString("versionDetail", Gson().toJson(response.body()))
                    }
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
        // If callback is null, no point in continuing
        if (callback == null) return

        val updateUrl = "${preferences.getString("serverURL", "")}"
        if (updateUrl.isEmpty()) {
            callback.notAvailable()
            return
        }

        val serverUrlMapper = ServerUrlMapper(context)
        val mapping = serverUrlMapper.processUrl(updateUrl)

        // Using structured concurrency with coroutines
        serviceScope.launch {
            try {
                // Check server reachability in parallel
                val primaryAvailableDeferred = async(Dispatchers.IO) {
                    isServerReachable(mapping.primaryUrl)
                }

                val alternativeAvailableDeferred = if (mapping.alternativeUrl != null) {
                    async(Dispatchers.IO) { isServerReachable(mapping.alternativeUrl) }
                } else {
                    CompletableDeferred<Boolean>().apply { complete(false) }
                }

                val primaryAvailable = primaryAvailableDeferred.await()
                val alternativeAvailable = alternativeAvailableDeferred.await()

                // If primary not available but alternative is, update preferences
                if (!primaryAvailable && alternativeAvailable && mapping.alternativeUrl != null) {
                    val uri = updateUrl.toUri()
                    withContext(Dispatchers.IO) {
                        preferences.edit {
                            serverUrlMapper.updateUrlPreferences(
                                this,
                                uri,
                                mapping.alternativeUrl,
                                mapping.primaryUrl,
                                preferences
                            )
                        }
                    }
                }

                // Use the right URL based on availability
                val urlToCheck = when {
                    primaryAvailable -> Utilities.getUpdateUrl(preferences)
                    alternativeAvailable -> mapping.alternativeUrl?.let { Utilities.getUpdateUrl(preferences) }
                    else -> null
                }

                if (urlToCheck == null) {
                    callback.notAvailable()
                    return@launch
                }

                // Make a single API call to check availability
                try {
                    val response = withContext(Dispatchers.IO) {
                        retrofitInterface?.isPlanetAvailable(urlToCheck)?.execute()
                    }

                    if (response?.code() == 200) {
                        callback.isAvailable()
                    } else {
                        callback.notAvailable()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    callback.notAvailable()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback.notAvailable()
            }
        }
    }

    fun becomeMember(realm: Realm, obj: JsonObject, callback: CreateUserCallback) {
        val username = obj["name"].asString
        if (isUserExists(realm, username)) {
            callback.onSuccess(context.getString(R.string.unable_to_create_user_user_already_exists))
            return
        }

        serviceScope.launch {
            val isPlanetAvailable = CompletableDeferred<Boolean>()

            isPlanetAvailable(object : PlanetAvailableListener {
                override fun isAvailable() {
                    isPlanetAvailable.complete(true)
                }

                override fun notAvailable() {
                    isPlanetAvailable.complete(false)
                }
            })

            if (isPlanetAvailable.await()) {
                try {
                    val userCheckUrl = "${Utilities.getUrl()}/_users/org.couchdb.user:${username}"
                    val response = withContext(Dispatchers.IO) {
                        retrofitInterface?.getJsonObject(Utilities.header, userCheckUrl)?.execute()
                    }

                    if (response?.body() != null && response.body()?.has("_id") == true) {
                        callback.onSuccess(context.getString(R.string.unable_to_create_user_user_already_exists))
                    } else {
                        val createUrl = "${Utilities.getUrl()}/_users/org.couchdb.user:${username}"
                        val putResponse = withContext(Dispatchers.IO) {
                            retrofitInterface?.putDoc(null, "application/json", createUrl, obj)?.execute()
                        }

                        if (putResponse?.isSuccessful == true && putResponse.body() != null && putResponse.body()!!.has("id")) {
                            launch(Dispatchers.IO) {
                                uploadToShelf(obj)
                            }

                            saveUserToDb(realm, putResponse.body()!!.get("id").asString, obj, callback)
                        } else {
                            callback.onSuccess(context.getString(R.string.unable_to_create_user_user_already_exists))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    callback.onSuccess(context.getString(R.string.unable_to_create_user_user_already_exists))
                }
            } else {
                val settings = MainApplication.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

                realm.executeTransactionAsync({ backgroundRealm ->
                    val model = populateUsersTable(obj, backgroundRealm, settings)
                    val keyString = generateKey()
                    val iv = generateIv()
                    if (model != null) {
                        model.key = keyString
                        model.iv = iv
                        }
                    },
                    {
                        Utilities.toast(MainApplication.context, context.getString(R.string.not_connect_to_planet_created_user_offline))
                        callback.onSuccess(context.getString(R.string.not_connect_to_planet_created_user_offline))
                    },
                    { error ->
                        error.printStackTrace()
                        callback.onSuccess(context.getString(R.string.unable_to_save_user_please_sync))
                    }
                )
            }
        }
    }

    private fun uploadToShelf(obj: JsonObject) {
        retrofitInterface?.putDoc(null, "application/json", Utilities.getUrl() + "/shelf/org.couchdb.user:" + obj["name"].asString, JsonObject())?.enqueue(object : Callback<JsonObject?> {
            override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {}

            override fun onFailure(call: Call<JsonObject?>, t: Throwable) {}
        })
    }

    @OptIn(FlowPreview::class)
    private fun saveUserToDb(realm: Realm, id: String, obj: JsonObject, callback: CreateUserCallback) {
        val settings = MainApplication.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        realm.executeTransactionAsync({ backgroundRealm ->
            try {
                val res = retrofitInterface?.getJsonObject(Utilities.header, "${Utilities.getUrl()}/_users/$id")?.execute()
                if (res?.isSuccessful == true && res.body() != null) {
                    populateUsersTable(res.body(), backgroundRealm, settings)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
            },
            {
                callback.onSuccess(context.getString(R.string.user_created_successfully))
                val userName = obj["name"].asString
                val userPassword = obj["password"]?.asString ?: ""

                MainApplication.applicationScope.launch {
                    withContext(Dispatchers.IO) {
                        val keyIvRealm = Realm.getDefaultInstance()
                        try {
                            keyIvRealm.executeTransaction { transactionRealm ->
                                val userModel = transactionRealm.where(RealmUserModel::class.java)
                                    .equalTo("name", userName).findFirst()

                                if (userModel != null) {
                                    val keyIvObj = JsonObject()
                                    keyIvObj.addProperty("name", userName)
                                    keyIvObj.addProperty("password", userPassword)

                                    UploadToShelfService(MainApplication.context).saveKeyIv(retrofitInterface, userModel, keyIvObj)
                                }
                            }
                        } finally {
                            keyIvRealm.close()
                        }
                    }
                }

                serviceScope.launch {
                    isNetworkConnectedFlow
                        .debounce(500).filter { isConnected -> isConnected }
                        .distinctUntilChanged().collect {
                            val serverUrl = settings.getString("serverURL", "")
                            if (!serverUrl.isNullOrEmpty()) {
                                val canReachServer = withContext(Dispatchers.IO) {
                                    isServerReachable(serverUrl)
                                }

                                if (canReachServer) {
                                    if (context is ProcessUserDataActivity) {
                                        withContext(Dispatchers.Main) {
                                            context.startUpload("becomeMember")
                                        }
                                    }

                                    withContext(Dispatchers.IO) {
                                        val backgroundRealm = Realm.getDefaultInstance()
                                        try {
                                            TransactionSyncManager.syncDb(backgroundRealm, "tablet_users")
                                        } finally {
                                            backgroundRealm.close()
                                        }
                                    }
                                }
                            }
                        }
                }
            },
            { error ->
                error.printStackTrace()
                callback.onSuccess(context.getString(R.string.unable_to_save_user_please_sync))
            }
        )
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
                                        val uri = currentUrl.toUri()
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
                                                    preferences.edit {
                                                        putString("parentCode", parentCode)
                                                    }
                                                }

                                                if (doc.has("models")) {
                                                    val modelsMap = doc.getAsJsonObject("models").entrySet()
                                                        .associate { it.key to it.value.asString }

                                                    withContext(Dispatchers.IO) {
                                                        preferences.edit {
                                                            putString("ai_models", Gson().toJson(modelsMap))
                                                        }
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
