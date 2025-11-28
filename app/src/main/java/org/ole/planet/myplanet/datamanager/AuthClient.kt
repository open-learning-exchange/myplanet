package org.ole.planet.myplanet.datamanager

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.ole.planet.myplanet.model.DocumentResponse
import org.ole.planet.myplanet.utilities.UrlUtils
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlinx.coroutines.delay

interface AuthApi {
    @GET
    suspend fun getDocuments(@Header("Authorization") header: String?, @Url url: String): retrofit2.Response<DocumentResponse>
}

object AuthClient {
    private const val MAX_RETRIES = 2
    private const val INITIAL_BACKOFF = 1000L

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private val api: AuthApi by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(UrlUtils.getUrl())
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(AuthApi::class.java)
    }

    suspend fun getDocuments(header: String?, url: String): retrofit2.Response<DocumentResponse> {
        var response: retrofit2.Response<DocumentResponse>? = null
        var exception: IOException? = null
        for (tryCount in 0..MAX_RETRIES) {
            try {
                response = api.getDocuments(header, url)
                if (response.isSuccessful || (response.code() in 400..499)) {
                    return response
                }
            } catch (e: IOException) {
                exception = e
            }

            if (tryCount < MAX_RETRIES) {
                val backoff = INITIAL_BACKOFF * 2.0.pow(tryCount.toDouble()).toLong()
                delay(backoff)
            }
        }
        return response ?: throw exception!!
    }
}
