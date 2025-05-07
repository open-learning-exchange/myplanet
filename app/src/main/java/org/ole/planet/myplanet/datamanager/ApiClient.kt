package org.ole.planet.myplanet.datamanager

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.reflect.Modifier
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
}
