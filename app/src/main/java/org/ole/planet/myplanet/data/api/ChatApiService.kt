package org.ole.planet.myplanet.data.api

import android.content.Context
import android.util.Log
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import okhttp3.RequestBody
import org.ole.planet.myplanet.model.ChatResponse
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils
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

            JsonUtils.gson.fromJson(
                responseString,
                object : TypeToken<Map<String, Boolean>>() {}.type
            )
        } catch (e: Exception) {
            Log.w("ChatApiService", "Failed to fetch AI providers from: ${UrlUtils.hostUrl}checkProviders/", e)
            null
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
