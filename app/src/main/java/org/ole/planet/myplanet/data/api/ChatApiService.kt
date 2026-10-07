package org.ole.planet.myplanet.data.api

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.RequestBody
import org.ole.planet.myplanet.model.ChatResponse
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.KJson
import org.ole.planet.myplanet.utils.UrlUtils
import retrofit2.Response

@Singleton
class ChatApiService @Inject constructor(
    private val apiInterface: ApiInterface,
    @param:ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider
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

            parseAiProviders(responseString)
        } catch (e: Exception) {
            Log.w("ChatApiService", "Failed to fetch AI providers from: ${UrlUtils.hostUrl}checkProviders/", e)
            null
        }
    }

    companion object {
        /**
         * Reads the `checkProviders/` body as Gson's `fromJson(text, Map<String, Boolean>)` did:
         * `null` for `null`, a string value read with `Boolean.parseBoolean`. Throws where Gson
         * threw: malformed text, a document that is not an object, or a number, object or array
         * value. A `null` value drops its provider, where Gson kept a `null` the screens failed on.
         */
        internal fun parseAiProviders(text: String): Map<String, Boolean>? {
            val root = KJson.parseOrNull(text) ?: return null
            if (root is JsonNull) return null
            val providers = root as? JsonObject ?: throw SerializationException("Expected an object but was $root")
            return buildMap {
                for ((name, available) in providers) {
                    when {
                        available is JsonNull -> Unit
                        available is JsonPrimitive && available.isString -> put(name, available.content.toBoolean())
                        available is JsonPrimitive && (available.content == "true" || available.content == "false") -> put(name, available.content.toBoolean())
                        else -> throw SerializationException("Expected a boolean but was $available")
                    }
                }
            }
        }
    }

    suspend fun sendChatRequest(content: RequestBody): Response<ChatResponse> {
        val hostUrl = UrlUtils.hostUrl
        if (hostUrl.isBlank()) {
            throw IllegalArgumentException("Host URL is not available")
        }
        return apiInterface.chatGpt(hostUrl, content)
    }
}
