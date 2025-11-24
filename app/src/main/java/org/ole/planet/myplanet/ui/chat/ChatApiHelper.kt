package org.ole.planet.myplanet.ui.chat

import android.content.Context
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.model.ChatModel
import org.ole.planet.myplanet.utilities.GsonUtils
import org.ole.planet.myplanet.utilities.UrlUtils
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@Singleton
class ChatApiHelper @Inject constructor(
    private val apiInterface: ApiInterface,
    @ApplicationContext private val context: Context
) {
    fun fetchAiProviders(result: (Map<String, Boolean>?) -> Unit) {
        try {
            val hostUrl = UrlUtils.hostUrl
            if (hostUrl.isBlank()) {
                result(null)
                return
            }
            
            val checkProvidersUrl = "${hostUrl}checkProviders/"

            apiInterface.checkAiProviders(checkProvidersUrl).enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    try {
                        when {
                            !response.isSuccessful -> {
                                result(null)
                                return
                            }

                            response.body() == null -> {
                                result(null)
                                return
                            }
                        }

                        val responseString = response.body()?.string()
                        if (responseString.isNullOrBlank()) {
                            result(null)
                            return
                        }

                        val providers: Map<String, Boolean> = GsonUtils.gson.fromJson(
                            responseString,
                            object : TypeToken<Map<String, Boolean>>() {}.type
                        )
                        result(providers)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        result(null)
                    }
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    try {
                        t.printStackTrace()
                        result(null)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        result(null)
                    }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
            result(null)
        }
    }

    fun sendChatRequest(content: RequestBody, callback: Callback<ChatModel>) {
        try {
            val hostUrl = UrlUtils.hostUrl
            if (hostUrl.isBlank()) {
                callback.onFailure(
                    apiInterface.chatGpt(hostUrl, content),
                    IllegalArgumentException("Host URL is not available")
                )
                return
            }

            apiInterface.chatGpt(hostUrl, content).enqueue(object : Callback<ChatModel> {
                override fun onResponse(call: Call<ChatModel>, response: Response<ChatModel>) {
                    try {
                        callback.onResponse(call, response)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback.onFailure(call, e)
                    }
                }

                override fun onFailure(call: Call<ChatModel>, t: Throwable) {
                    try {
                        t.printStackTrace()
                        callback.onFailure(call, t)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback.onFailure(call, e)
                    }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                callback.onFailure(
                    apiInterface.chatGpt(UrlUtils.hostUrl, content),
                    e
                )
            } catch (callbackError: Exception) {
                callbackError.printStackTrace()
            }
        }
    }
}
