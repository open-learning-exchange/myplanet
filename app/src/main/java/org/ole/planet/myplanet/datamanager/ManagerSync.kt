package org.ole.planet.myplanet.datamanager

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.realm.Realm
import java.util.Locale
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.datamanager.DatabaseService
import kotlin.LazyThreadSafetyMode
import org.ole.planet.myplanet.model.RealmUserModel.Companion.populateUsersTable
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.androidDecrypter
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.UrlUtils
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ManagerSync private constructor(
    private val context: Context,
    private val dbService: DatabaseService
) {
    private val settings: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun login(userName: String?, password: String?, listener: SyncListener) {
        try {
            if (userName.isNullOrBlank() || password.isNullOrBlank()) {
                listener.onSyncFailed("Username and password are required.")
                return
            }
            
            listener.onSyncStarted()
            
            val apiInterface = ApiClient.client.create(ApiInterface::class.java)
            if (apiInterface == null) {
                listener.onSyncFailed("Network client not available.")
                return
            }
            
            val authHeader = try {
                "Basic " + Base64.encodeToString("$userName:$password".toByteArray(), Base64.NO_WRAP)
            } catch (e: Exception) {
                e.printStackTrace()
                listener.onSyncFailed("Authentication encoding failed.")
                return
            }
            
            val userUrl = try {
                String.format("%s/_users/%s", UrlUtils.getUrl(), "org.couchdb.user:$userName")
            } catch (e: Exception) {
                e.printStackTrace()
                listener.onSyncFailed("Invalid server URL.")
                return
            }

            apiInterface.getJsonObject(authHeader, userUrl).enqueue(object : Callback<JsonObject?> {
                override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                    try {
                        when {
                            !response.isSuccessful -> {
                                val errorMsg = when (response.code()) {
                                    401 -> "Name or password is incorrect."
                                    404 -> "User not found."
                                    500 -> "Server error. Please try again later."
                                    else -> "Login failed. Error code: ${response.code()}"
                                }
                                listener.onSyncFailed(errorMsg)
                                return
                            }

                            response.body() == null -> {
                                listener.onSyncFailed("Empty response from server.")
                                return
                            }
                        }

                        val jsonDoc = response.body()
                        if (jsonDoc?.has("derived_key") == true && jsonDoc.has("salt")) {
                            try {
                                val derivedKey = jsonDoc["derived_key"].asString
                                val salt = jsonDoc["salt"].asString

                                if (androidDecrypter(userName, password, derivedKey, salt)) {
                                    dbService.withRealm { realm ->
                                        checkManagerAndInsert(jsonDoc, realm, listener)
                                    }
                                } else {
                                    listener.onSyncFailed("Authentication failed. Invalid credentials.")
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                listener.onSyncFailed("Authentication processing failed.")
                            }
                        } else {
                            listener.onSyncFailed("Server response missing authentication data.")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        listener.onSyncFailed("Login processing failed.")
                    }
                }

                override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                    try {
                        t.printStackTrace()
                        val errorMsg = when (t) {
                            is java.net.UnknownHostException -> "Server not reachable. Check your internet connection."
                            is java.net.SocketTimeoutException -> "Connection timeout. Please try again."
                            is java.net.ConnectException -> "Unable to connect to server."
                            else -> "Network error: ${t.message ?: "Unknown error"}"
                        }
                        listener.onSyncFailed(errorMsg)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        listener.onSyncFailed("Network error occurred.")
                    }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
            listener.onSyncFailed("Login initialization failed.")
        }
    }

    fun syncAdmin() {
        try {
            val `object` = JsonObject()
            val selector = JsonObject()
            selector.addProperty("isUserAdmin", true)
            `object`.add("selector", selector)
            
            val apiInterface = ApiClient.client.create(ApiInterface::class.java)
            if (apiInterface == null) {
                return
            }
            
            val header = UrlUtils.header
            if (header.isBlank()) {
                return
            }
            
            val url = try {
                UrlUtils.getUrl() + "/_users/_find"
            } catch (e: Exception) {
                e.printStackTrace()
                return
            }

            apiInterface.findDocs(header, "application/json", url, `object`).enqueue(object : Callback<JsonObject?> {
                override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                    try {
                        if (response.isSuccessful && response.body() != null) {
                            val responseBody = response.body()
                            settings.edit { putString("communityLeaders", "$responseBody") }

                            val array = JsonUtils.getJsonArray("docs", responseBody)
                            if (array != null && array.size() > 0) {
                                try {
                                    settings.edit { putString("user_admin", Gson().toJson(array[0])) }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                    try {
                        t.printStackTrace()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        @JvmStatic
        val instance: ManagerSync by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            ManagerSync(MainApplication.context, MainApplication.service)
        }
    }
}
