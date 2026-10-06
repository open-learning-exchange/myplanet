package org.ole.planet.myplanet.di

import android.net.TrafficStats
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import java.lang.reflect.Modifier
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import javax.net.SocketFactory
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.ole.planet.myplanet.data.api.JvmKtorPlatform
import org.ole.planet.myplanet.data.api.KtorHttpClients
import org.ole.planet.myplanet.data.api.KtorPlanetApi
import org.ole.planet.myplanet.data.api.PlanetApi
import org.ole.planet.myplanet.data.api.RetryInterceptor
import org.ole.planet.myplanet.utils.Constants.NETWORK_TRAFFIC_TAG

private class TaggedSocketFactory(private val delegate: SocketFactory) : SocketFactory() {
    private fun tag() = TrafficStats.setThreadStatsTag(NETWORK_TRAFFIC_TAG)
    override fun createSocket(): Socket { tag(); return delegate.createSocket() }
    override fun createSocket(host: String, port: Int): Socket { tag(); return delegate.createSocket(host, port) }
    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket { tag(); return delegate.createSocket(host, port, localHost, localPort) }
    override fun createSocket(host: InetAddress, port: Int): Socket { tag(); return delegate.createSocket(host, port) }
    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket { tag(); return delegate.createSocket(address, port, localAddress, localPort) }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StandardHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ReachabilityHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlainGson

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 60L
    private const val WRITE_TIMEOUT_SECONDS = 120L
    private const val REACHABILITY_TIMEOUT_SECONDS = 5L

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .excludeFieldsWithModifiers(Modifier.FINAL, Modifier.TRANSIENT, Modifier.STATIC)
            .serializeNulls()
            .create()
    }

    @Provides
    @Singleton
    @PlainGson
    fun providePlainGson(): Gson {
        return Gson()
    }

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }

    private const val MAX_REQUESTS_PER_HOST = 20

    @Provides
    @Singleton
    fun provideConnectionPool(): ConnectionPool {
        return ConnectionPool(MAX_REQUESTS_PER_HOST, 5, TimeUnit.MINUTES)
    }

    private fun buildOkHttpClient(
        connect: Long,
        read: Long,
        write: Long,
        connectionPool: ConnectionPool,
        retryInterceptor: RetryInterceptor? = null
    ): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequestsPerHost = MAX_REQUESTS_PER_HOST
        }
        val builder = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(connectionPool)
            .connectTimeout(connect, TimeUnit.SECONDS)
            .readTimeout(read, TimeUnit.SECONDS)
            .writeTimeout(write, TimeUnit.SECONDS)
            .socketFactory(TaggedSocketFactory(SocketFactory.getDefault()))

        if (retryInterceptor != null) {
            builder.addInterceptor(retryInterceptor)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    @StandardHttpClient
    fun provideStandardOkHttpClient(
        retryInterceptor: RetryInterceptor,
        connectionPool: ConnectionPool
    ): OkHttpClient {
        return buildOkHttpClient(
            CONNECT_TIMEOUT_SECONDS,
            READ_TIMEOUT_SECONDS,
            WRITE_TIMEOUT_SECONDS,
            connectionPool,
            retryInterceptor
        )
    }

    @Provides
    @Singleton
    @ReachabilityHttpClient
    fun provideReachabilityOkHttpClient(
        connectionPool: ConnectionPool
    ): OkHttpClient {
        return buildOkHttpClient(
            REACHABILITY_TIMEOUT_SECONDS,
            REACHABILITY_TIMEOUT_SECONDS,
            REACHABILITY_TIMEOUT_SECONDS,
            connectionPool
        )
    }

    /**
     * App-lifetime and never closed: it runs on the shared @StandardHttpClient, and closing it
     * would evict that client's connection pool and shut down its dispatcher.
     */
    @Provides
    @Singleton
    fun provideKtorHttpClient(@StandardHttpClient okHttpClient: OkHttpClient): HttpClient {
        return KtorHttpClients.create(okHttpClient)
    }

    @Provides
    @Singleton
    fun providePlanetApi(httpClient: HttpClient, json: Json): PlanetApi {
        return KtorPlanetApi(httpClient, json, JvmKtorPlatform)
    }
}
