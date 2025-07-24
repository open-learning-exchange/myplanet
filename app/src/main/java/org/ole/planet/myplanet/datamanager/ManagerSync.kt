package org.ole.planet.myplanet.datamanager

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import io.realm.Realm
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.di.ManagerSyncEntryPoint
import org.ole.planet.myplanet.model.RealmUserModel.Companion.populateUsersTable
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.androidDecrypter
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.Utilities
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@Singleton
class ManagerSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dbService: DatabaseService,
    @AppPreferences private val settings: SharedPreferences
) {
    private val mRealm: Realm = dbService.realmInstance

    fun login(userName: String?, password: String?, listener: SyncListener) {
        listener.onSyncStarted()
        val apiInterface = ApiClient.client?.create(ApiInterface::class.java)
        apiInterface?.getJsonObject("Basic " + Base64.encodeToString("$userName:$password".toByteArray(), Base64.NO_WRAP), String.format("%s/_users/%s", Utilities.getUrl(), "org.couchdb.user:$userName"))
            ?.enqueue(object : Callback<JsonObject?> {
                override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                    if (response.isSuccessful && response.body() != null) {
                        val jsonDoc = response.body()
                        if (jsonDoc?.has("derived_key") == true && jsonDoc.has("salt")) {
//                          val decrypt = AndroidDecrypter()
                            val derivedKey = jsonDoc["derived_key"].asString
                            val salt = jsonDoc["salt"].asString
                            if (androidDecrypter(userName, password, derivedKey, salt)) {
                                checkManagerAndInsert(jsonDoc, mRealm, listener)
                            } else {
                                listener.onSyncFailed("Name or password is incorrect.")
                            }
                        } else {
                            listener.onSyncFailed("JSON response is missing required keys.")
                        }
                    } else {
                        listener.onSyncFailed("Name or password is incorrect.")
                    }
                }

                override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                    listener.onSyncFailed("Server not reachable.")
                }
            })
    }

    fun syncAdmin() {
        val `object` = JsonObject()
        val selector = JsonObject()
        selector.addProperty("isUserAdmin", true)
        `object`.add("selector", selector)
        val apiInterface = ApiClient.client?.create(ApiInterface::class.java)
        apiInterface?.findDocs(Utilities.header, "application/json", Utilities.getUrl() + "/_users/_find", `object`)?.enqueue(object : Callback<JsonObject?> {
            override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                if (response.body() != null) {
                    settings.edit { putString("communityLeaders", "${response.body()}") }
                    val array = JsonUtils.getJsonArray("docs", response.body())
                    if (array.size() > 0) {
                        settings.edit { putString("user_admin", Gson().toJson(array[0])) }
                    }
                }
            }

            override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                t.printStackTrace()
            }
        })
    }

    private fun checkManagerAndInsert(jsonDoc: JsonObject?, realm: Realm, listener: SyncListener) {
        if (isManager(jsonDoc)) {
            populateUsersTable(jsonDoc, realm, settings)
            listener.onSyncComplete()
        } else {
            listener.onSyncFailed(MainApplication.context.getString(R.string.user_verification_in_progress))
        }
    }

    private fun isManager(jsonDoc: JsonObject?): Boolean {
        val roles = jsonDoc?.get("roles")?.asJsonArray
        val isManager = roles.toString().lowercase(Locale.getDefault()).contains("manager")
        return jsonDoc?.get("isUserAdmin")?.asBoolean == true || isManager
    }

    companion object {
        private var ourInstance: ManagerSync? = null
        @JvmStatic
        val instance: ManagerSync?
            get() {
                if (ourInstance == null) {
                    ourInstance = EntryPointAccessors.fromApplication(
                        MainApplication.context,
                        ManagerSyncEntryPoint::class.java
                    ).managerSync()
                }
                return ourInstance
            }
    }
}
