package org.ole.planet.myplanet.datamanager

import com.google.gson.GsonBuilder
import java.io.IOException
import java.lang.reflect.Modifier
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import org.ole.planet.myplanet.utilities.RetryUtils

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
        return RetryUtils.retry(
            maxAttempts = 3,
            delayMs = 2000L,
            shouldRetry = { resp -> resp == null || !resp.isSuccessful },
            block = operation,
        )
    }
}
