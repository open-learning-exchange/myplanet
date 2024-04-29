package org.ole.planet.myplanet.datamanager

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.net.Uri
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.realm.Realm
import okhttp3.ResponseBody
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.model.RealmCommunity
import org.ole.planet.myplanet.model.RealmUserModel.Companion.isUserExists
import org.ole.planet.myplanet.model.RealmUserModel.Companion.populateUsersTable
import org.ole.planet.myplanet.service.UploadToShelfService
import org.ole.planet.myplanet.ui.sync.ProcessUserDataActivity
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.generateIv
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.generateKey
import org.ole.planet.myplanet.utilities.Constants.KEY_UPGRADE_MAX
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.Constants.showBetaFeature
import org.ole.planet.myplanet.utilities.DialogUtils.CustomProgressDialog
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.Sha256Utils
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.VersionUtils
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException

class Service(private val context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val retrofitInterface: ApiInterface? = ApiClient.client?.create(ApiInterface::class.java)

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
        if (settings.getString("couchdbURL", "")?.isEmpty() == true) {
            callback.onError(context.getString(R.string.config_not_available), true)
            return
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
                                if (responses.isEmpty()) {
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
                                    } else {
                                        callback.onError("Planet up to date", false)
                                    }
                                }
                            } catch (e: Exception) {
                                e.localizedMessage?.let { Log.e("Error", it) }
                                callback.onError("New apk version required  but not found on server - Contact admin", false)
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
        retrofitInterface?.isPlanetAvailable(Utilities.getUpdateUrl(preferences))?.enqueue(object : Callback<ResponseBody?> {
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
            override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                if (response.isSuccessful) {
                    Utilities.log("Successful uploaded to shelf")
                } else {
                    Utilities.log("Failed to upload to shelf")
                }
            }

            override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                t.message?.let { Utilities.log(it) }
            }
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
        }, { callback.onSuccess("User created successfully") }) { error: Throwable ->
            error.printStackTrace()
            callback.onSuccess("Unable to save user please sync")
        }
    }

    fun syncPlanetServers(realm: Realm, callback: SuccessListener) {
        retrofitInterface?.getJsonObject("", "https://planet.earth.ole.org/db/communityregistrationrequests/_all_docs?include_docs=true")?.enqueue(object : Callback<JsonObject?> {
            override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                if (response.body() != null) {
                    val arr = JsonUtils.getJsonArray("rows", response.body())
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
                        callback.onSuccess("Unable to connect to planet earth")
                    }
                }
            }

            override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                realm.close()
                callback.onSuccess("Unable to connect to planet earth")
            }
        })
    }

    fun getConfig(listener: ConfigurationIdListener?, url: String, pin: String) {
        val customProgressDialog = CustomProgressDialog(context).apply {
            setText("Checking Server")
            show()
        }
        val uri = Uri.parse(url)
        val url_user: String
        val url_pwd: String
        val couchdbURL: String
        if (url.contains("@")) {
            val userinfo = getUserInfo(uri)
            url_user = userinfo[0]
            url_pwd = userinfo[1]
            couchdbURL = url
        } else {
            url_user = "satellite"
            url_pwd = pin
            couchdbURL = uri.scheme + "://" + url_user + ":" + url_pwd + "@" + uri.host + ":" + if (uri.port == -1) (if (uri.scheme == "http") 80 else 443) else uri.port
        }

        val header= "Basic " + Base64.encodeToString(("$url_user:$url_pwd").toByteArray(), Base64.NO_WRAP)
        retrofitInterface?.getConfiguration(header, getUrl(couchdbURL) + "/configurations/_all_docs?include_docs=true")?.enqueue(object : Callback<JsonObject?> {
            override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                if (response.isSuccessful) {
                    val jsonObject = response.body()
                    val rows = jsonObject?.getAsJsonArray("rows")
                    if (rows != null && rows.size() > 0) {
                        val firstRow = rows.get(0).asJsonObject
                        val id = firstRow.getAsJsonPrimitive("id").asString
                        val doc = firstRow.getAsJsonObject("doc")
                        val code = doc.getAsJsonPrimitive("code").asString
                        listener?.onConfigurationIdReceived(id, code)
                        customProgressDialog.dismiss()
                    }
                } else {
                    customProgressDialog.dismiss()
                    showAlertDialog(context.getString(R.string.failed_to_get_configuration_id))
                }
            }

            override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                t.message?.let { Utilities.log(it) }
                customProgressDialog.dismiss()
                showAlertDialog(context.getString(R.string.device_couldn_t_reach_server_check_and_try_again))
            }
        })
    }

    fun showAlertDialog(message: String?) {
        val builder = AlertDialog.Builder(context)
        builder.setMessage(message)
        builder.setCancelable(true)
        builder.setNegativeButton(R.string.okay) { dialog: DialogInterface, _: Int -> dialog.cancel() }
        val alert = builder.create()
        alert.show()
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
        fun onConfigurationIdReceived(id: String, code: String)
    }
}
