package org.ole.planet.myplanet.data.api

import android.content.Context
import android.util.Log
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import okhttp3.RequestBody
import org.ole.planet.myplanet.model.ChatResponse
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.GsonUtils
import org.ole.planet.myplanet.utils.SecurePrefs
import org.ole.planet.myplanet.utils.UrlUtils
import retrofit2.Response

@Singleton
class ChatApiService @Inject constructor(
    private val apiInterface: ApiInterface,
    @param:ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val sharedPrefManager: SharedPrefManager
) {
    suspend fun fetchAiProviders(): Map<String, Boolean>? {
        return try {
            val hostUrl = UrlUtils.hostUrl
            if (hostUrl.isBlank()) {
                return null
            }

            val checkProvidersUrl = "${hostUrl}checkProviders/"
            val response = apiInterface.checkAiProviders(checkProvidersUrl)

            if (!response.isSuccessful || response.body() == null) {
                return null
            }

            val responseString = withContext(dispatcherProvider.io) {
                response.body()?.string()
            }
            if (responseString.isNullOrBlank()) {
                return null
            }

            parseProviders(responseString)
        } catch (e: Exception) {
            Log.w("ChatApiService", "Failed to fetch AI providers from: ${UrlUtils.hostUrl}checkProviders/", e)
            null
        }
    }

    private fun parseProviders(json: String): Map<String, Boolean>? {
        val root = JsonParser.parseString(json)
        if (!root.isJsonObject) return null
        val obj = root.asJsonObject
        val providers = obj.get("providers")
        if (providers != null && providers.isJsonArray) {
            return providers.asJsonArray
                .filter { it.isJsonObject }
                .mapNotNull { element ->
                    val provider = element.asJsonObject
                    val name = provider.get("name")?.takeIf { it.isJsonPrimitive }?.asString
                    if (name.isNullOrBlank()) return@mapNotNull null
                    val configured = provider.get("configured")
                        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
                        ?.asBoolean ?: false
                    name to configured
                }
                .toMap()
        }
        return GsonUtils.gson.fromJson(json, object : TypeToken<Map<String, Boolean>>() {}.type)
    }

    suspend fun sendChatRequest(content: RequestBody): Response<ChatResponse> {
        val hostUrl = UrlUtils.hostUrl
        if (hostUrl.isBlank()) {
            throw IllegalArgumentException("Host URL is not available")
        }
        return apiInterface.chatGpt(hostUrl, content, userAuthHeader())
    }
    
    private suspend fun userAuthHeader(): String? = withContext(dispatcherProvider.io) {
        val prefs = sharedPrefManager.rawPreferences
        val name = SecurePrefs.getUserName(context, prefs)
        val password = SecurePrefs.getPassword(context, prefs)
        if (name.isNullOrEmpty() || password.isNullOrEmpty() || name != sharedPrefManager.getUserName()) {
            null
        } else {
            UrlUtils.basicAuthHeader(name, password)
        }
    }
}
