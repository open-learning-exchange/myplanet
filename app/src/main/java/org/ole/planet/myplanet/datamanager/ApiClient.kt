package org.ole.planet.myplanet.datamanager

import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.lang.reflect.Modifier
import java.util.concurrent.TimeUnit
import kotlin.math.pow

object ApiClient {
    private const val BASE_URL = "https://vi.media.mit.edu/"
    private var retrofit: Retrofit? = null
    @JvmStatic
    val client: Retrofit?
        get() {
            val client = OkHttpClient.Builder().connectTimeout(1, TimeUnit.MINUTES).readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS).retryOnConnectionFailure(true).addInterceptor(RetryInterceptor())
                .build()

            if (retrofit == null) {
                retrofit = Retrofit.Builder()
                    .baseUrl(BASE_URL).client(client)
                    .addConverterFactory(GsonConverterFactory.create(
                        GsonBuilder()
                            .excludeFieldsWithModifiers(Modifier.FINAL, Modifier.TRANSIENT, Modifier.STATIC)
                            .serializeNulls()
                            .setLenient()
                            .create()
                        )
                    ).build()
            }
            return retrofit
        }

    class RetryInterceptor : Interceptor {
        val maxRetryCount = 3
        val retryDelayMillis = 1000L

        override fun intercept(chain: Interceptor.Chain): Response {
            var attempt = 0
            var response: Response? = null
            val request = chain.request()
            val url = request.url().toString()

            while (true) {
                try {
                    response?.close()
                    response = chain.proceed(request)

                    if (response.isSuccessful || response.code() == 404) {
                        return response
                    } else if (response.code() in listOf(401, 403)) {
                        response.close()
                        throw IOException("Authentication failed with status code: ${response.code()}")
                    } else {
                        response.close()
                        throw IOException("Unexpected status code: ${response.code()}")
                    }
                } catch (e: IOException) {
                    attempt++
                    if (attempt >= maxRetryCount) {
                        throw IOException("Failed after $maxRetryCount attempts: $url", e)
                    }

                    val delayMillis = retryDelayMillis * 2.0.pow((attempt - 1).toDouble()).toLong()
                    Thread.sleep(delayMillis)
                }
            }
        }
    }
}
