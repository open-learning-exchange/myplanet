package org.ole.planet.myplanet.datamanager

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.net.Uri
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
import org.ole.planet.myplanet.BuildConfig
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.MainApplication.Companion.applicationScope
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
import org.ole.planet.myplanet.utilities.Sha256Utils
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.VersionUtils
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import kotlin.math.min

class Service(private val context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val retrofitInterface: ApiInterface? = ApiClient.client?.create(ApiInterface::class.java)

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun healthAccess(listener: SuccessListener) {
        retrofitInterface?.healthAccess(Utilities.getHealthAccessUrl(preferences))?.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.code() == 200) {
                    listener.onSuccess("Successfully synced")
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
        Log.d("Service", "checkVersion: ${Utilities.getUpdateUrl(settings)}")
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
                    Log.d("Service", "checkVersion onResponse: ${response.body()}")
                    retrofitInterface.getApkVersion(Utilities.getApkVersionUrl(settings)).enqueue(object : Callback<ResponseBody> {
                        override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                            Log.d("Service", "getApkVersion onResponse: $response")
                            val responses: String?
                            try {
                                responses = Gson().fromJson(response.body()?.string(), String::class.java)
                                if (responses == null || responses.isEmpty()) {
                                    callback.onError("Planet up to date", false)
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
                                        Log.d("Service", "checkVersion: Planet up to date")
                                    } else {
                                        callback.onError("Planet up to date", false)
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                callback.onError("New apk version required but not found on server - Contact admin", false)
                            }
                        }
                        override fun onFailure(call: Call<ResponseBody>, t: Throwable) {}
                    })
                } else {
                    callback.onError("Version not found", true)
                }
            }

            override fun onFailure(call: Call<MyPlanet?>, t: Throwable) {
                t.printStackTrace()
                callback.onError("Connection failed.", true)
            }
        })
    }

    fun isPlanetAvailable(callback: PlanetAvailableListener?) {
        Log.d("Service", "isPlanetAvailable: ${Utilities.getUpdateUrl(preferences)}")
        retrofitInterface?.isPlanetAvailable(Utilities.getUpdateUrl(preferences))?.enqueue(object : Callback<ResponseBody?> {
            override fun onResponse(call: Call<ResponseBody?>, response: Response<ResponseBody?>) {
                if (callback != null && response.code() == 200) {
                    callback.isAvailable()
                    Log.d("Service", "isAvailable: true")
                } else {
                    callback?.notAvailable()
                    Log.d("Service", "isAvailable: false")
                }
            }

            override fun onFailure(call: Call<ResponseBody?>, t: Throwable) {
                callback?.notAvailable()
            }
        })
    }

    fun becomeMember(realm: Realm, obj: JsonObject, callback: CreateUserCallback) {
        isPlanetAvailable(object : PlanetAvailableListener {
            override fun isAvailable() {
                retrofitInterface?.getJsonObject(Utilities.header, "${Utilities.getUrl()}/_users/org.couchdb.user:${obj["name"].asString}")?.enqueue(object : Callback<JsonObject> {
                    override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                        if (response.body() != null && response.body()?.has("_id") == true) {
                            callback.onSuccess("Unable to create user, user already exists")
                        } else {
                            retrofitInterface.putDoc(null, "application/json", "${Utilities.getUrl()}/_users/org.couchdb.user:${obj["name"].asString}", obj).enqueue(object : Callback<JsonObject> {
                                override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                                    if (response.body() != null && response.body()!!.has("id")) {
                                        uploadToShelf(obj)
                                        saveUserToDb(realm, response.body()!!.get("id").asString, obj, callback)
                                    } else {
                                        callback.onSuccess("Unable to create user")
                                    }
                                }

                                override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                                    callback.onSuccess("Unable to create user")
                                }
                            })
                        }
                    }

                    override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                        callback.onSuccess("Unable to create user")
                    }
                })
            }

            override fun notAvailable() {
                val settings = MainApplication.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                if (isUserExists(realm, obj["name"].asString)) {
                    callback.onSuccess("User already exists")
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
                Utilities.toast(MainApplication.context, "Not connected to planet, created user offline.")
                callback.onSuccess("Not connected to planet, created user offline.")
            }
        })
    }

    private fun uploadToShelf(obj: JsonObject) {
        retrofitInterface?.putDoc(null, "application/json", Utilities.getUrl() + "/shelf/org.couchdb.user:" + obj["name"].asString, JsonObject())?.enqueue(object : Callback<JsonObject?> {
            override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {}

            override fun onFailure(call: Call<JsonObject?>, t: Throwable) {}
        })
    }

    private fun saveUserToDb(realm: Realm, id: String, obj: JsonObject, callback: CreateUserCallback) {
        val settings = MainApplication.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        realm.executeTransactionAsync({ realm1: Realm? ->
            try {
                val res = retrofitInterface?.getJsonObject(Utilities.header, Utilities.getUrl() + "/_users/" + id)?.execute()
                if (res?.body() != null) {
                    val model = populateUsersTable(res.body(), realm1, settings)
                    if (model != null) {
                        UploadToShelfService(MainApplication.context).saveKeyIv(retrofitInterface, model, obj)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }, {
            callback.onSuccess("User created successfully")
            isNetworkConnectedFlow.onEach { isConnected ->
                if (isConnected) {
                    val serverUrl = settings.getString("serverURL", "")
                    if (!serverUrl.isNullOrEmpty()) {
                        serviceScope.launch {
                            val canReachServer = withContext(Dispatchers.IO) {
                                isServerReachable(serverUrl)
                            }
                            if (canReachServer) {
                                if (context is ProcessUserDataActivity) {
                                    context.runOnUiThread {
                                        context.startUpload("becomeMember")
                                    }
                                }
                                TransactionSyncManager.syncDb(realm, "tablet_users")
                            }
                        }
                    }
                }
            }.launchIn(serviceScope)
        }) { error: Throwable ->
            error.printStackTrace()
            callback.onSuccess("Unable to save user please sync")
        }
    }

    fun syncPlanetServers(realm: Realm, callback: SuccessListener) {
        retrofitInterface?.getJsonObject("", "https://planet.earth.ole.org/db/communityregistrationrequests/_all_docs?include_docs=true")?.enqueue(object : Callback<JsonObject?> {
            override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                if (response.body() != null) {
                    val arr = JsonUtils.getJsonArray("rows", response.body())
                    if (!realm.isClosed) {
                        realm.executeTransactionAsync({ realm1: Realm ->
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
                        }, {
                            realm.close()
                            callback.onSuccess("Server sync successfully")
                        }) { error: Throwable ->
                            realm.close()
                            error.printStackTrace()
                        }
                    }
                }
            }

            override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                realm.close()
            }
        })
    }

    fun getMinApk(listener: ConfigurationIdListener?, url: String, pin: String, activity: SyncActivity) {
        val overallStartTime = System.currentTimeMillis()
        Log.d("PerformanceLog", "Starting getMinApk process")

        // Create a list of URLs to try, starting with the original URL
        val urlsToTry = mutableListOf(url)

        val serverMappings = mapOf(
            "http://${BuildConfig.PLANET_URIUR_URL}" to "https://${BuildConfig.PLANET_URIUR_CLONE_URL}",
            "http://${BuildConfig.PLANET_EMBAKASI_URL}" to "https://${BuildConfig.PLANET_EMBAKASI_CLONE_URL}",
        )

        // Add alternative URL from serverMappings if available
        serverMappings[url]?.let { alternativeUrl ->
            urlsToTry.add(alternativeUrl)
            Log.d("PerformanceLog", "Added alternative URL: $alternativeUrl")
        }

        // Use coroutine scope to perform concurrent URL checks
        applicationScope.launch {
            val dialogStartTime = System.currentTimeMillis()
            val customProgressDialog = withContext(Dispatchers.Main) {
                CustomProgressDialog(context).apply {
                    setText(context.getString(R.string.check_apk_version))
                    show()
                }
            }
            Log.d("PerformanceLog", "Dialog creation took: ${System.currentTimeMillis() - dialogStartTime}ms")

            try {
                // Use withContext(Dispatchers.IO) for network operations
                val networkStartTime = System.currentTimeMillis()
                val result = withContext(Dispatchers.IO) {
                    // Create a list of deferred jobs for concurrent network calls
                    val deferredResults = urlsToTry.map { currentUrl ->
                        async {
                            try {
                                val urlStartTime = System.currentTimeMillis()
                                Log.d("PerformanceLog", "Starting check for URL: $currentUrl")

                                // Check versions concurrently
                                val versionCheckStartTime = System.currentTimeMillis()
                                val versionsResponse = try {
                                    Log.d("NetworkLog", "Attempting to connect to $currentUrl/versions")
                                    val response = retrofitInterface?.getConfiguration("$currentUrl/versions")?.execute()
                                    val checkDuration = System.currentTimeMillis() - versionCheckStartTime
                                    Log.d("PerformanceLog", "Version check for $currentUrl took: ${checkDuration}ms")

                                    if (checkDuration >= 60000) {
                                        Log.w("NetworkLog", "Connection to $currentUrl took excessive time (${checkDuration}ms)")
                                    }
                                    response
                                } catch (e: java.net.ConnectException) {
                                    Log.e("NetworkLog", "Connection failed for $currentUrl: ${e.message}")
                                    Log.d("NetworkLog", "Moving to next URL in fallback list")
                                    null
                                } catch (e: java.net.SocketTimeoutException) {
                                    Log.e("NetworkLog", "Connection timed out for $currentUrl: ${e.message}")
                                    Log.d("NetworkLog", "Moving to next URL in fallback list")
                                    null
                                } catch (e: Exception) {
                                    Log.e("NetworkLog", "Unexpected error connecting to $currentUrl", e)
                                    Log.d("NetworkLog", "Moving to next URL in fallback list")
                                    null
                                }

                                if (versionsResponse?.isSuccessful == true) {
                                    val jsonObject = versionsResponse.body()
                                    val currentVersion = "${context.resources.getText(R.string.app_version)}"
                                    val minApkVersion = jsonObject?.get("minapk")?.asString
                                    Log.d("PerformanceLog", "Version parsing successful for $currentUrl")

                                    // Version check
                                    if (minApkVersion != null && isVersionAllowed(currentVersion, minApkVersion)) {
                                        // Perform configuration check for this URL
                                        val configStartTime = System.currentTimeMillis()
                                        val uri = Uri.parse(currentUrl)
                                        val couchdbURL = if (currentUrl.contains("@")) {
                                            getUserInfo(uri)
                                            currentUrl
                                        } else {
                                            val urlUser = "satellite"
                                            Log.d("okuro", "called")
                                            "${uri.scheme}://$urlUser:$pin@${uri.host}:${if (uri.port == -1) if (uri.scheme == "http") 80 else 443 else uri.port}"
                                        }

                                        withContext(Dispatchers.Main) {
                                            customProgressDialog.setText(context.getString(R.string.checking_server))
                                        }

                                        val configResponse = retrofitInterface?.getConfiguration("${getUrl(couchdbURL)}/configurations/_all_docs?include_docs=true")?.execute()
                                        Log.d("PerformanceLog", "Configuration check for $currentUrl took: ${System.currentTimeMillis() - configStartTime}ms")

                                        if (configResponse?.isSuccessful == true) {
                                            val rows = configResponse.body()?.getAsJsonArray("rows")
                                            if (rows != null && rows.size() > 0) {
                                                val firstRow = rows.get(0).asJsonObject
                                                val id = firstRow.getAsJsonPrimitive("id").asString
                                                val doc = firstRow.getAsJsonObject("doc")
                                                val code = doc.getAsJsonPrimitive("code").asString
                                                Log.d("PerformanceLog", "Total processing time for successful URL $currentUrl: ${System.currentTimeMillis() - urlStartTime}ms")
                                                return@async UrlCheckResult.Success(id, code, currentUrl)
                                            }
                                        }
                                    }
                                }
                                Log.d("PerformanceLog", "URL $currentUrl failed after ${System.currentTimeMillis() - urlStartTime}ms")
                                return@async UrlCheckResult.Failure(currentUrl)
                            } catch (e: Exception) {
                                Log.e("PerformanceLog", "Exception while processing $currentUrl", e)
                                return@async UrlCheckResult.Failure(currentUrl)
                            }
                        }
                    }

                    // Wait for the first successful result
                    val awaitStartTime = System.currentTimeMillis()
                    val result = deferredResults.awaitFirst { it is UrlCheckResult.Success }
                    Log.d("PerformanceLog", "Awaiting first successful result took: ${System.currentTimeMillis() - awaitStartTime}ms")
                    result
                }
                Log.d("PerformanceLog", "Total network operations took: ${System.currentTimeMillis() - networkStartTime}ms")

                // Handle the result
                when (result) {
                    is UrlCheckResult.Success -> {
                        val isAlternativeUrl = result.url != url
                        listener?.onConfigurationIdReceived(result.id, result.code, result.url, isAlternativeUrl)
                        activity.setSyncFailed(false)
                        Log.d("PerformanceLog", "Successfully connected to URL: ${result.url}")
                    }
                    is UrlCheckResult.Failure -> {
                        activity.setSyncFailed(true)
                        val errorMessage = when (extractProtocol(url)) {
                            context.getString(R.string.http_protocol) -> context.getString(R.string.device_couldn_t_reach_local_server)
                            context.getString(R.string.https_protocol) -> context.getString(R.string.device_couldn_t_reach_nation_server)
                            else -> context.getString(R.string.device_couldn_t_reach_local_server)
                        }
                        withContext(Dispatchers.Main) {
                            showAlertDialog(errorMessage, false)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PerformanceLog", "Exception in main process", e)
                activity.setSyncFailed(true)
                withContext(Dispatchers.Main) {
                    showAlertDialog(
                        context.getString(R.string.device_couldn_t_reach_local_server),
                        false
                    )
                }
            } finally {
                customProgressDialog.dismiss()
                Log.d("PerformanceLog", "Total process time: ${System.currentTimeMillis() - overallStartTime}ms")
            }
        }
    }

//    fun getMinApk(listener: ConfigurationIdListener?, url: String, pin: String, activity: SyncActivity) {
//        // Create a list of URLs to try, starting with the original URL
//        val urlsToTry = mutableListOf(url)
//
//        val serverMappings = mapOf(
//            "http://${BuildConfig.PLANET_URIUR_URL}" to "http://35.231.161.29",
//            "http://192.168.1.202" to "http://34.35.29.147",
//            "https://${BuildConfig.PLANET_GUATEMALA_URL}" to "http://guatemala.com/cloudserver",
//            "http://${BuildConfig.PLANET_XELA_URL}" to "http://xela.com/cloudserver",
//            "http://${BuildConfig.PLANET_SANPABLO_URL}" to "http://sanpablo.com/cloudserver",
//            "http://${BuildConfig.PLANET_EMBAKASI_URL}" to "http://embakasi.com/cloudserver",
//            "https://${BuildConfig.PLANET_VI_URL}" to "http://vi.com/cloudserver"
//        )
//
//        // Add alternative URL from serverMappings if available
//        serverMappings[url]?.let { alternativeUrl ->
//            urlsToTry.add(alternativeUrl)
//        }
//
//        // Use coroutine scope to perform concurrent URL checks
//        applicationScope.launch {
//            val customProgressDialog = withContext(Dispatchers.Main) {
//                CustomProgressDialog(context).apply {
//                    setText(context.getString(R.string.check_apk_version))
//                    show()
//                }
//            }
//
//            try {
//                // Use withContext(Dispatchers.IO) for network operations
//                val result = withContext(Dispatchers.IO) {
//                    // Create a list of deferred jobs for concurrent network calls
//                    val deferredResults = urlsToTry.map { currentUrl ->
//                        async {
//                            try {
//                                Log.d("Service", "Checking URL: $currentUrl")
//                                // Check versions concurrently
//                                val versionsResponse = retrofitInterface?.getConfiguration("$currentUrl/versions")?.execute()
//
//                                if (versionsResponse?.isSuccessful == true) {
//                                    val jsonObject = versionsResponse.body()
//                                    val currentVersion = "${context.resources.getText(R.string.app_version)}"
//                                    val minApkVersion = jsonObject?.get("minapk")?.asString
//                                    Log.d("okuro", "first config check is successful")
//
//                                    // Version check
//                                    if (minApkVersion != null && isVersionAllowed(currentVersion, minApkVersion)) {
//                                        // Perform configuration check for this URL
//                                        val uri = Uri.parse(currentUrl)
//                                        val couchdbURL = if (currentUrl.contains("@")) {
//                                            getUserInfo(uri)
//                                            currentUrl
//                                        } else {
//                                            val urlUser = "satellite"
//                                            "${uri.scheme}://$urlUser:$pin@${uri.host}:${if (uri.port == -1) if (uri.scheme == "http") 80 else 443 else uri.port}"
//                                        }
//                                        withContext(Dispatchers.Main) {
//                                            customProgressDialog.setText(context.getString(R.string.checking_server))
//                                        }
//                                        val configResponse = retrofitInterface.getConfiguration("${getUrl(couchdbURL)}/configurations/_all_docs?include_docs=true").execute()
//
//                                        if (configResponse.isSuccessful) {
//                                            Log.d("okuro", "second config check is successful")
//                                            val rows = configResponse.body()?.getAsJsonArray("rows")
//                                            if (rows != null && rows.size() > 0) {
//                                                val firstRow = rows.get(0).asJsonObject
//                                                val id = firstRow.getAsJsonPrimitive("id").asString
//                                                val doc = firstRow.getAsJsonObject("doc")
//                                                val code = doc.getAsJsonPrimitive("code").asString
//                                                return@async UrlCheckResult.Success(id, code, currentUrl)
//                                            }
//                                        }
//                                    }
//                                }
//                                return@async UrlCheckResult.Failure(currentUrl)
//                            } catch (e: Exception) {
//                                e.printStackTrace()
//                                return@async UrlCheckResult.Failure(currentUrl)
//                            }
//                        }
//                    }
//
//                    // Wait for the first successful result
//                    deferredResults.awaitFirst { it is UrlCheckResult.Success }
//                }
//
//                // Handle the result
//                when (result) {
//                    is UrlCheckResult.Success -> {
//                        val isAlternativeUrl = result.url != url
//                        listener?.onConfigurationIdReceived(result.id, result.code, result.url, isAlternativeUrl)
//                        activity.setSyncFailed(false)
//                        Log.d("Service", "Successfully connected to URL: ${result.url}")
//                    }
//                    is UrlCheckResult.Failure -> {
//                        activity.setSyncFailed(true)
//                        val errorMessage = when (extractProtocol(url)) {
//                            context.getString(R.string.http_protocol) -> context.getString(R.string.device_couldn_t_reach_local_server)
//                            context.getString(R.string.https_protocol) -> context.getString(R.string.device_couldn_t_reach_nation_server)
//                            else -> context.getString(R.string.device_couldn_t_reach_local_server)
//                        }
//                        showAlertDialog(errorMessage, false)
//                    }
//                }
//            } catch (e: Exception) {
//                e.printStackTrace()
//                activity.setSyncFailed(true)
//                showAlertDialog(context.getString(R.string.device_couldn_t_reach_local_server), false)
//            } finally {
//                customProgressDialog.dismiss()
//            }
//        }
//    }

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
        applicationScope.launch(Dispatchers.Main) {
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
        fun onConfigurationIdReceived(id: String, code: String, url: String, isAlternativeUrl: Boolean)
    }
}
