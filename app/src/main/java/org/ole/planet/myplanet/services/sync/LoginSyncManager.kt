package org.ole.planet.myplanet.services.sync

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.repository.UserSyncRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.AndroidDecrypter.Companion.androidDecrypter
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.UrlUtils

@Singleton
class LoginSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPrefManager: SharedPrefManager,
    private val userSyncRepository: UserSyncRepository,
    private val apiInterface: ApiInterface,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider
) {

    suspend fun login(userName: String?, password: String?, listener: OnSyncListener) {
        try {
            if (userName.isNullOrBlank() || password.isNullOrBlank()) {
                listener.onSyncFailed("Username and password are required.")
                return
            }

            listener.onSyncStarted()

            val authHeader = try {
                UrlUtils.basicAuthHeader(userName, password)
            } catch (e: Exception) {
                Log.e("LoginSyncManager", "Authentication encoding failed", e)
                listener.onSyncFailed("Authentication encoding failed.")
                return
            }

            val userUrl = try {
                "${UrlUtils.getUrl()}/_users/org.couchdb.user:$userName"
            } catch (e: Exception) {
                Log.e("LoginSyncManager", "Invalid server URL", e)
                listener.onSyncFailed("Invalid server URL.")
                return
            }

            try {
                val response = apiInterface.getJsonObject(authHeader, userUrl)
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
                        val isAuthenticated = withContext(dispatcherProvider.default) {
                            androidDecrypter(userName, password, derivedKey, salt)
                        }

                        if (isAuthenticated) {
                            checkManagerAndInsert(jsonDoc, listener)
                        } else {
                            listener.onSyncFailed("Authentication failed. Invalid credentials.")
                        }
                    } catch (e: Exception) {
                        Log.e("LoginSyncManager", "Authentication processing failed", e)
                        listener.onSyncFailed("Authentication processing failed.")
                    }
                } else {
                    listener.onSyncFailed("Server response missing authentication data.")
                }
            } catch (t: Exception) {
                try {
                    Log.e("LoginSyncManager", "Network error during login", t)
                    val errorMsg = when (t) {
                        is UnknownHostException -> "Server not reachable. Check your internet connection."
                        is SocketTimeoutException -> "Connection timeout. Please try again."
                        is ConnectException -> "Unable to connect to server."
                        else -> "Network error: ${t.message ?: "Unknown error"}"
                    }
                    listener.onSyncFailed(errorMsg)
                } catch (e: Exception) {
                    Log.e("LoginSyncManager", "Error handling network failure", e)
                    listener.onSyncFailed("Network error occurred.")
                }
            }
        } catch (e: Exception) {
            Log.e("LoginSyncManager", "Login initialization failed", e)
            listener.onSyncFailed("Login initialization failed.")
        }
    }

    fun syncAdmin() {
        applicationScope.launch {
            try {
                val `object` = JsonObject()
                val selector = JsonObject()
                selector.addProperty("isUserAdmin", true)
                `object`.add("selector", selector)

                val header = UrlUtils.header
                if (header.isBlank()) {
                    return@launch
                }

                val url = try {
                    UrlUtils.getUrl() + "/_users/_find"
                } catch (e: Exception) {
                    Log.e("LoginSyncManager", "Error constructing find admin URL", e)
                    return@launch
                }

                try {
                    val response = apiInterface.postDoc(header, "application/json", url, `object`)
                    if (response.isSuccessful && response.body() != null) {
                        val responseBody = response.body()
                        sharedPrefManager.setCommunityLeaders("$responseBody")

                        val array = JsonUtils.getJsonArray("docs", responseBody)
                        if (!array.isEmpty()) {
                            try {
                                sharedPrefManager.setRawString("user_admin", JsonUtils.gson.toJson(array[0]))
                            } catch (e: Exception) {
                                Log.e("LoginSyncManager", "Error saving user_admin JSON", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("LoginSyncManager", "Admin sync request failed", e)
                }
            } catch (e: Exception) {
                Log.e("LoginSyncManager", "Error in syncAdmin", e)
            }
        }
    }

    private suspend fun checkManagerAndInsert(jsonDoc: JsonObject?, listener: OnSyncListener) {
        if (!isManager(jsonDoc)) {
            listener.onSyncFailed(context.getString(R.string.user_verification_in_progress))
            return
        }

        userSyncRepository.saveUser(jsonDoc)
        listener.onSyncComplete()
    }

    private fun isManager(jsonDoc: JsonObject?): Boolean {
        val roles = jsonDoc?.get("roles")?.asJsonArray
        var isManager = false
        roles?.forEach { role ->
            if (role.isJsonPrimitive && role.asString.equals("manager", ignoreCase = true)) {
                isManager = true
            }
        }
        return jsonDoc?.get("isUserAdmin")?.asBoolean == true || isManager
    }
}
