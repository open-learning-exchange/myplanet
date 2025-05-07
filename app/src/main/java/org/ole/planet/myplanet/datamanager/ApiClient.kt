package org.ole.planet.myplanet.datamanager

import android.util.Log
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
            val httpClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS).writeTimeout(10, TimeUnit.SECONDS).build()
            if (retrofit == null) {
                retrofit = Retrofit.Builder()
                    .baseUrl(BASE_URL).client(httpClient).addConverterFactory(
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
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)    // Increased from 10 to 60 seconds
            .readTimeout(120, TimeUnit.SECONDS)      // Increased from 10 to 120 seconds
            .writeTimeout(60, TimeUnit.SECONDS)      // Increased from 10 to 60 seconds
            .build()

        // Create a new Retrofit instance with the enhanced client
        val enhancedRetrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)  // Use the constant directly
            .client(httpClient)
            .addConverterFactory(
                GsonConverterFactory.create(
                    GsonBuilder()
                        .excludeFieldsWithModifiers(Modifier.FINAL, Modifier.TRANSIENT, Modifier.STATIC)
                        .serializeNulls().create()
                )
            ).build()

        return enhancedRetrofit.create(ApiInterface::class.java)
    }

    /**
     * Execute a network operation with retry logic
     */
    fun <T> executeWithRetry(operation: () -> Response<T>?): Response<T>? {
        var retryCount = 0
        var response: Response<T>? = null
        var lastException: Exception? = null

        while (response == null && retryCount < 3) {
            try {
                response = operation()
                if (response?.isSuccessful == false) {
                    Log.e("SYNC", "Request failed with code ${response.code()}")
                    if (retryCount < 2) {
                        response = null // Force retry
                    }
                }
            } catch (e: SocketTimeoutException) {
                lastException = e
                Log.e("SYNC", "Timeout on attempt $retryCount: ${e.message}")
            } catch (e: IOException) {
                lastException = e
                Log.e("SYNC", "IO error on attempt $retryCount: ${e.message}")
            } catch (e: Exception) {
                lastException = e
                Log.e("SYNC", "Error on attempt $retryCount: ${e.message}")
            }

            if (response == null && retryCount < 2) {
                retryCount++
                // Exponential backoff before retry
                val sleepTime = (2000L * (retryCount + 1))
                Log.d("SYNC", "Retrying request in ${sleepTime}ms (attempt ${retryCount + 1})")
                Thread.sleep(sleepTime)
            } else {
                break
            }
        }

        if (response == null && lastException != null) {
            Log.e("SYNC", "All retry attempts failed", lastException)
        }

        return response
    }
}
