package org.ole.planet.myplanet.datamanager

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.lang.reflect.Modifier
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val BASE_URL = "https://vi.media.mit.edu/"
    private var retrofit: Retrofit? = null
    @JvmStatic
    val client: Retrofit?
        get() {
            val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS).writeTimeout(10, TimeUnit.SECONDS).build()
            if (retrofit == null) {
                retrofit = Retrofit.Builder()
                    .baseUrl(BASE_URL).client(client).addConverterFactory(
                        GsonConverterFactory.create(
                            GsonBuilder()
                        .excludeFieldsWithModifiers(Modifier.FINAL, Modifier.TRANSIENT, Modifier.STATIC)
                        .serializeNulls().create()
                        )
                    ).build()
            }
            return retrofit
        }

    fun getEnhancedClient(): ApiInterface {
        val httpClient = OkHttpClient.Builder().connectTimeout(60, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()

        val enhancedRetrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(
                GsonConverterFactory.create(GsonBuilder()
                    .excludeFieldsWithModifiers(Modifier.FINAL, Modifier.TRANSIENT, Modifier.STATIC)
                    .serializeNulls().create()
                )
            ).build()

        return enhancedRetrofit.create(ApiInterface::class.java)
    }

    fun <T> executeWithRetry(operation: () -> Response<T>?): Response<T>? {
        var retryCount = 0
        var response: Response<T>? = null
        var lastException: Exception? = null

        while (response == null && retryCount < 3) {
            try {
                response = operation()
                if (response?.isSuccessful == false) {
                    if (retryCount < 2) {
                        response = null
                    }
                }
            } catch (e: SocketTimeoutException) {
                lastException = e
            } catch (e: IOException) {
                lastException = e
            } catch (e: Exception) {
                lastException = e
            }

            if (response == null && retryCount < 2) {
                retryCount++
                val sleepTime = (2000L * (retryCount + 1))
                Thread.sleep(sleepTime)
            } else {
                break
            }
        }

        return response
    }
}
