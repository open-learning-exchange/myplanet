package org.ole.planet.myplanet.datamanager

import com.google.gson.GsonBuilder
import java.io.IOException
import java.lang.reflect.Modifier
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.ole.planet.myplanet.utilities.RetryUtils
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    private const val BASE_URL = "https://vi.media.mit.edu/"

    private val gsonConverter by lazy {
        GsonConverterFactory.create(
            GsonBuilder()
                .excludeFieldsWithModifiers(Modifier.FINAL, Modifier.TRANSIENT, Modifier.STATIC)
                .serializeNulls()
                .create()
        )
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(gsonConverter)
            .build()
    }

    @JvmStatic
    val client: Retrofit
        get() = retrofit

    fun getEnhancedClient(): ApiInterface {
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(
                GsonConverterFactory.create(
                    GsonBuilder()
                        .excludeFieldsWithModifiers(Modifier.FINAL, Modifier.TRANSIENT, Modifier.STATIC)
                        .serializeNulls()
                        .create()
                )
            )
            .build()
    }

    @JvmStatic
    val client: Retrofit
        get() = retrofit

    private val enhancedOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val enhancedRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(enhancedOkHttpClient)
            .addConverterFactory(
                GsonConverterFactory.create(
                    GsonBuilder()
                        .excludeFieldsWithModifiers(Modifier.FINAL, Modifier.TRANSIENT, Modifier.STATIC)
                        .serializeNulls()
                        .create(),
                ),
            )
            .build()
    }

    fun getEnhancedClient(): ApiInterface {
        return enhancedRetrofit.create(ApiInterface::class.java)
    }

    fun <T> executeWithRetry(operation: () -> Response<T>?): Response<T>? {
        return RetryUtils.retry(
            maxAttempts = 3,
            delayMs = 2000L,
            shouldRetry = { resp -> resp == null || !resp.isSuccessful },
            block = operation,
        )
    }

    fun <T> executeWithResult(operation: () -> Response<T>?): NetworkResult<T> {
        var retryCount = 0
        var lastException: Exception? = null

        while (retryCount < 3) {
            try {
                val response = operation()
                if (response != null) {
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null) {
                            return NetworkResult.Success(body)
                        }
                        return NetworkResult.Error(response.code(), null)
                    } else if (retryCount < 2) {
                        retryCount++
                        Thread.sleep(2000L * (retryCount + 1))
                        continue
                    } else {
                        val errorBody = try { response.errorBody()?.string() } catch (_: Exception) { null }
                        return NetworkResult.Error(response.code(), errorBody)
                    }
                }
            } catch (e: SocketTimeoutException) {
                lastException = e
            } catch (e: IOException) {
                lastException = e
            } catch (e: Exception) {
                lastException = e
            }

            if (retryCount < 2) {
                retryCount++
                Thread.sleep(2000L * (retryCount + 1))
            } else {
                break
            }
        }
        return NetworkResult.Exception(lastException ?: Exception("Unknown error"))
    }
}
