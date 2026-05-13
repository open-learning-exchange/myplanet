package org.ole.planet.myplanet.services.sync

import android.content.Context
import android.util.Base64
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.repository.UserRepository
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
    private val userRepository: UserRepository,
    private val userSyncRepository: UserSyncRepository,
    private val apiInterface: ApiInterface,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider
) {
    fun login(userName: String?, password: String?, listener: OnSyncListener) {
        applicationScope.launch(dispatcherProvider.io) {
            try {
                if (userName.isNullOrBlank() || password.isNullOrBlank()) {
                    withContext(dispatcherProvider.main) { listener.onSyncFailed("Username and password are required.") }
                    return@launch
                }

                withContext(dispatcherProvider.main) { listener.onSyncStarted() }

                val authHeader = try {
                    "Basic " + Base64.encodeToString("$userName:$password".toByteArray(), Base64.NO_WRAP)
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(dispatcherProvider.main) { listener.onSyncFailed("Authentication encoding failed.") }
                    return@launch
                }

                val userUrl = try {
                    String.format("%s/_users/%s", UrlUtils.getUrl(), "org.couchdb.user:$userName")
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(dispatcherProvider.main) { listener.onSyncFailed("Invalid server URL.") }
                    return@launch
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
                            withContext(dispatcherProvider.main) { listener.onSyncFailed(errorMsg) }
                            return@launch
                        }

                        response.body() == null -> {
                            withContext(dispatcherProvider.main) { listener.onSyncFailed("Empty response from server.") }
                            return@launch
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
                                withContext(dispatcherProvider.main) {
                                    listener.onSyncFailed("Authentication failed. Invalid credentials.")
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(dispatcherProvider.main) {
                                listener.onSyncFailed("Authentication processing failed.")
                            }
                        }
                    } else {
                        withContext(dispatcherProvider.main) { listener.onSyncFailed("Server response missing authentication data.") }
                    }
                } catch (t: Exception) {
                    try {
                        t.printStackTrace()
                        val errorMsg = when (t) {
                            is java.net.UnknownHostException -> "Server not reachable. Check your internet connection."
                            is java.net.SocketTimeoutException -> "Connection timeout. Please try again."
                            is java.net.ConnectException -> "Unable to connect to server."
                            else -> "Network error: ${t.message ?: "Unknown error"}"
                        }
                        withContext(dispatcherProvider.main) { listener.onSyncFailed(errorMsg) }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(dispatcherProvider.main) { listener.onSyncFailed("Network error occurred.") }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(dispatcherProvider.main) { listener.onSyncFailed("Login initialization failed.") }
            }
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
                    e.printStackTrace()
                    return@launch
                }

                try {
                    val response = apiInterface.findDocs(header, "application/json", url, `object`)
                    if (response.isSuccessful && response.body() != null) {
                        val responseBody = response.body()
                        sharedPrefManager.setCommunityLeaders("$responseBody")

                        val array = JsonUtils.getJsonArray("docs", responseBody)
                        if (array.size() > 0) {
                            try {
                                sharedPrefManager.setRawString("user_admin", JsonUtils.gson.toJson(array[0]))
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun checkManagerAndInsert(jsonDoc: JsonObject?, listener: OnSyncListener) {
        if (!isManager(jsonDoc) && !sharedPrefManager.getFastSync()) {
            withContext(dispatcherProvider.main) {
                listener.onSyncFailed(context.getString(R.string.user_verification_in_progress))
            }
            return
        }

        userSyncRepository.saveUser(jsonDoc)
        withContext(dispatcherProvider.main) {
            listener.onSyncComplete()
        }
    }

    private fun isManager(jsonDoc: JsonObject?): Boolean {
        val roles = jsonDoc?.get("roles")?.asJsonArray
        val isManager = roles.toString().lowercase(Locale.getDefault()).contains("manager")
        return jsonDoc?.get("isUserAdmin")?.asBoolean == true || isManager
    }
}
