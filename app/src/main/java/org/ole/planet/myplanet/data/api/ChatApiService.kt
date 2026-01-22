package org.ole.planet.myplanet.data.api

import android.content.Context
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.model.ChatModel
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.UrlUtils
import retrofit2.Callback
import retrofit2.Response

@Singleton
class ChatApiService @Inject constructor(
    private val apiInterface: ApiInterface,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    fun fetchAiProviders(result: (Map<String, Boolean>?) -> Unit) {
        applicationScope.launch {
            try {
                val hostUrl = UrlUtils.hostUrl
                if (hostUrl.isBlank()) {
                    withContext(Dispatchers.Main) {
                        result(null)
                    }
                    return@launch
                }

                val checkProvidersUrl = "${hostUrl}checkProviders/"

                try {
                    val response = withContext(Dispatchers.IO) {
                        apiInterface.checkAiProviders(checkProvidersUrl)
                    }

                    withContext(Dispatchers.Main) {
                        try {
                            when {
                                !response.isSuccessful -> {
                                    result(null)
                                    return@withContext
                                }

                                response.body() == null -> {
                                    result(null)
                                    return@withContext
                                }
                            }

                            val responseString = response.body()?.string()
                            if (responseString.isNullOrBlank()) {
                                result(null)
                                return@withContext
                            }

                            val providers: Map<String, Boolean> = JsonUtils.gson.fromJson(
                                responseString,
                                object : TypeToken<Map<String, Boolean>>() {}.type
                            )
                            result(providers)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            result(null)
                        }
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                    withContext(Dispatchers.Main) {
                        result(null)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    result(null)
                }
            }
        }
    }

    fun sendChatRequest(content: RequestBody, callback: Callback<ChatModel>) {
        applicationScope.launch {
            try {
                val hostUrl = UrlUtils.hostUrl
                if (hostUrl.isBlank()) {
                    withContext(Dispatchers.Main) {
                        try {
                            callback.onFailure(
                                object : retrofit2.Call<ChatModel> {
                                    override fun clone(): retrofit2.Call<ChatModel> = this
                                    override fun execute(): retrofit2.Response<ChatModel> = throw UnsupportedOperationException()
                                    override fun enqueue(callback: Callback<ChatModel>) {}
                                    override fun isExecuted(): Boolean = false
                                    override fun cancel() {}
                                    override fun isCanceled(): Boolean = false
                                    override fun request(): okhttp3.Request = okhttp3.Request.Builder().url("http://localhost").build()
                                    override fun timeout(): okio.Timeout = okio.Timeout.NONE
                                },
                                IllegalArgumentException("Host URL is not available")
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    return@launch
                }

                try {
                    val response = withContext(Dispatchers.IO) {
                        apiInterface.chatGpt(hostUrl, content)
                    }

                    withContext(Dispatchers.Main) {
                        try {
                            // We need to pass a Call object, but we used suspend function.
                            // We can pass a dummy Call object or adapt the callback interface if possible.
                            // However, refactoring the caller to use suspend or result callback would be better.
                            // But for now, to maintain compatibility:
                            val dummyCall = object : retrofit2.Call<ChatModel> {
                                override fun clone(): retrofit2.Call<ChatModel> = this
                                override fun execute(): retrofit2.Response<ChatModel> = response
                                override fun enqueue(callback: Callback<ChatModel>) {}
                                override fun isExecuted(): Boolean = true
                                override fun cancel() {}
                                override fun isCanceled(): Boolean = false
                                override fun request(): okhttp3.Request = response.raw().request
                                override fun timeout(): okio.Timeout = okio.Timeout.NONE
                            }

                            if (response.isSuccessful) {
                                callback.onResponse(dummyCall, response)
                            } else {
                                // onFailure usually expects Throwable, onResponse handles error codes.
                                // Retrofit calls onResponse even for 4xx/5xx.
                                callback.onResponse(dummyCall, response)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            // callback.onFailure expects Call and Throwable
                            // We can't easily construct a valid Call that matches the original request fully without more effort.
                        }
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                    withContext(Dispatchers.Main) {
                         val dummyCall = object : retrofit2.Call<ChatModel> {
                                override fun clone(): retrofit2.Call<ChatModel> = this
                                override fun execute(): retrofit2.Response<ChatModel> = throw t
                                override fun enqueue(callback: Callback<ChatModel>) {}
                                override fun isExecuted(): Boolean = true
                                override fun cancel() {}
                                override fun isCanceled(): Boolean = false
                                override fun request(): okhttp3.Request = okhttp3.Request.Builder().url(hostUrl).build()
                                override fun timeout(): okio.Timeout = okio.Timeout.NONE
                         }
                        callback.onFailure(dummyCall, t)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
