package org.ole.planet.myplanet.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.lang.reflect.Modifier
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient
import org.ole.planet.myplanet.data.api.ApiClient
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.api.RetryInterceptor
import org.ole.planet.myplanet.service.BroadcastService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StandardHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StandardRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .excludeFieldsWithModifiers(Modifier.FINAL, Modifier.TRANSIENT, Modifier.STATIC)
            .serializeNulls()
            .create()
    }

    private fun buildOkHttpClient(connect: Long, read: Long, write: Long, retryInterceptor: RetryInterceptor? = null): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(connect, TimeUnit.SECONDS)
            .readTimeout(read, TimeUnit.SECONDS)
            .writeTimeout(write, TimeUnit.SECONDS)

        if (retryInterceptor != null) {
            builder.addInterceptor(retryInterceptor)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    @StandardHttpClient
    fun provideStandardOkHttpClient(broadcastService: BroadcastService): OkHttpClient {
        return buildOkHttpClient(10, 10, 10, RetryInterceptor(broadcastService))
    }

    @Provides
    @Singleton
    @StandardRetrofit
    fun provideStandardRetrofit(
        @StandardHttpClient okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://vi.media.mit.edu/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideApiInterface(@StandardRetrofit retrofit: Retrofit): ApiInterface {
        return retrofit.create(ApiInterface::class.java)
    }

    @Provides
    @Singleton
    fun provideApiClient(
        @StandardRetrofit retrofit: Retrofit,
    ): ApiClient {
        ApiClient.client = retrofit
        return ApiClient
    }
}
