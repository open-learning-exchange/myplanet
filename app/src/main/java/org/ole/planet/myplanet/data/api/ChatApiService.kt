package org.ole.planet.myplanet.data.api

import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.ChatResponse
import org.ole.planet.myplanet.utils.AppLog
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.GsonUtils
import org.ole.planet.myplanet.utils.UrlUtils

@Singleton
class ChatApiService @Inject constructor(
    private val planetApi: PlanetApi,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend fun fetchAiProviders(): Map<String, Boolean>? {
        return try {
            val hostUrl = UrlUtils.hostUrl
            if (hostUrl.isBlank()) {
                return null
            }

            val checkProvidersUrl = "${hostUrl}checkProviders/"
            val response = planetApi.checkAiProviders(checkProvidersUrl)

            if (!response.isSuccessful) {
                return null
            }

            val responseString = withContext(dispatcherProvider.io) {
                response.body
            }
            if (responseString.isNullOrBlank()) {
                return null
            }

            GsonUtils.gson.fromJson(
                responseString,
                object : TypeToken<Map<String, Boolean>>() {}.type
            )
        } catch (e: Exception) {
            AppLog.w("ChatApiService", "Failed to fetch AI providers from: ${UrlUtils.hostUrl}checkProviders/", e)
            null
        }
    }

    suspend fun sendChatRequest(content: UploadBody): ApiResponse<ChatResponse> {
        val hostUrl = UrlUtils.hostUrl
        if (hostUrl.isBlank()) {
            throw IllegalArgumentException("Host URL is not available")
        }
        return planetApi.chatGpt(hostUrl, content)
    }
}
