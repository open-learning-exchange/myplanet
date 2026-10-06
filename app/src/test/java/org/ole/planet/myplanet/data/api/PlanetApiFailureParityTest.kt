package org.ole.planet.myplanet.data.api

import io.ktor.client.HttpClient
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.di.NetworkModule

/**
 * Network failures through both [PlanetApi] implementations on identically configured OkHttp
 * clients: callers branch on the exception type (UnknownHostException, SocketTimeoutException,
 * ConnectException, IOException), so type and message must be the same.
 */
class PlanetApiFailureParityTest {

    private lateinit var server: MockWebServer
    private val ktorClients = mutableListOf<HttpClient>()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        ktorClients.forEach { it.close() }
        server.close()
    }

    private fun apis(okHttp: () -> OkHttpClient): List<PlanetApi> {
        val json = NetworkModule.provideJson()
        val retrofit = RetrofitPlanetApi(
            NetworkModule.provideApiInterface(NetworkModule.provideStandardRetrofit(okHttp(), NetworkModule.provideGson(), json))
        )
        val client = NetworkModule.provideKtorHttpClient(okHttp()).also { ktorClients += it }
        return listOf(retrofit, KtorPlanetApi(client, json, JvmKtorPlatform))
    }

    /** Runs [call] on both implementations and returns the failure each threw, as "Type: message". */
    private fun failures(okHttp: () -> OkHttpClient, before: () -> Unit = {}, call: suspend PlanetApi.() -> Unit): List<String> =
        apis(okHttp).map { api ->
            before()
            try {
                runBlocking { api.call() }
                "no failure"
            } catch (e: Exception) {
                "${e::class.java.name}: ${e.message}"
            }
        }

    /**
     * Both implementations threw [expectedType]. Messages are compared too unless [sameMessage] is
     * false: OkHttp itself words a read timeout either "timeout" or "Read timed out", depending on
     * whether okio's watchdog or the socket's own timeout fires first.
     */
    private fun assertSameFailure(expectedType: Class<out Exception>, failures: List<String>, sameMessage: Boolean = true) {
        val compared = if (sameMessage) failures else failures.map { it.substringBefore(':') }
        assertEquals("Retrofit vs Ktor", compared[0], compared[1])
        assertEquals(failures.toString(), expectedType.name, failures[0].substringBefore(':'))
    }

    @Test
    fun unknownHost_throwsUnknownHostException() {
        val failures = failures({ OkHttpClient.Builder().dns { host -> throw UnknownHostException("no such host: $host") }.build() }) {
            getJsonObject(null, "http://planet.invalid/db/_all_dbs")
        }

        assertSameFailure(UnknownHostException::class.java, failures)
    }

    @Test
    fun connectTimeout_throwsSocketTimeoutException() {
        val failures = failures({ OkHttpClient.Builder().socketFactory(TimingOutSocketFactory).build() }) {
            isPlanetAvailable(server.url("/db/_all_dbs").toString())
        }

        assertSameFailure(SocketTimeoutException::class.java, failures)
    }

    @Test
    fun readTimeout_beforeHeaders_throwsSocketTimeoutException() {
        val failures = failures(
            { OkHttpClient.Builder().readTimeout(200, TimeUnit.MILLISECONDS).build() },
            before = { server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.Stall).build()) },
        ) {
            getJsonObject(null, server.url("/db/users").toString())
        }

        assertSameFailure(SocketTimeoutException::class.java, failures, sameMessage = false)
    }

    @Test
    fun readTimeout_whileStreaming_throwsSocketTimeoutException() {
        val failures = failures(
            { OkHttpClient.Builder().readTimeout(200, TimeUnit.MILLISECONDS).build() },
            before = {
                server.enqueue(MockResponse.Builder().setHeader("Content-Length", 1_000).onResponseBody(SocketEffect.Stall).build())
            },
        ) {
            requireNotNull(downloadFile(null, server.url("/big.bin").toString()).body).use { it.source().readByteArray() }
        }

        assertSameFailure(SocketTimeoutException::class.java, failures, sameMessage = false)
    }

    /** A socket whose connect always times out, as OkHttp's platform layer reports it. */
    private object TimingOutSocketFactory : SocketFactory() {
        private class TimingOutSocket : Socket() {
            override fun connect(endpoint: SocketAddress?, timeout: Int) {
                throw SocketTimeoutException("connect timed out")
            }
        }

        override fun createSocket(): Socket = TimingOutSocket()
        override fun createSocket(host: String, port: Int): Socket = TimingOutSocket()
        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket = TimingOutSocket()
        override fun createSocket(host: InetAddress, port: Int): Socket = TimingOutSocket()
        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket = TimingOutSocket()
    }
}
