package org.ole.planet.myplanet.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import org.ole.planet.myplanet.datamanager.UploadApiInterface
import org.ole.planet.myplanet.utilities.UrlUtils
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UploadHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UploadRetrofit

@Module
@InstallIn(SingletonComponent::class)
object UploadNetworkModule {

    @Provides
    @Singleton
    @UploadHttpClient
    fun provideUploadOkHttpClient(@StandardHttpClient okHttpClient: OkHttpClient): OkHttpClient {
        return okHttpClient.newBuilder().build()
    }

    @Provides
    @Singleton
    @UploadRetrofit
    fun provideUploadRetrofit(
        @UploadHttpClient okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(UrlUtils.getUrl())
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideUploadApiInterface(@UploadRetrofit retrofit: Retrofit): UploadApiInterface {
        return retrofit.create(UploadApiInterface::class.java)
    }
}
