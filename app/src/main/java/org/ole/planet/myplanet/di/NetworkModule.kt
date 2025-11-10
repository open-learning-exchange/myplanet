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
import org.ole.planet.myplanet.datamanager.ApiClient
import org.ole.planet.myplanet.datamanager.ApiInterface
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class HealthCheckHttpClient

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

    private fun buildOkHttpClient(connect: Long, read: Long, write: Long): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(connect, TimeUnit.SECONDS)
            .readTimeout(read, TimeUnit.SECONDS)
            .writeTimeout(write, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @HealthCheckHttpClient
    fun provideHealthCheckOkHttpClient(): OkHttpClient {
        return buildOkHttpClient(5, 5, 5)
    }

    @Provides
    @Singleton
    fun provideHealthCheckApi(
        @HealthCheckHttpClient okHttpClient: OkHttpClient,
        gson: Gson
    ): HealthCheckApi {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://localhost/") // Base URL is overridden by @Url
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        return retrofit.create(HealthCheckApi::class.java)
    }

    @Provides
    @Singleton
    @StandardHttpClient
    fun provideStandardOkHttpClient(): OkHttpClient {
        return buildOkHttpClient(10, 10, 10)
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
