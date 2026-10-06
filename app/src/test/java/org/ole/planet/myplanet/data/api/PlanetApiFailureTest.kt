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
 * Network failures through [KtorPlanetApi]: callers branch on the exception type
 * (UnknownHostException, SocketTimeoutException, ConnectException, IOException), so each must
 * surface as the exception OkHttp itself threw, with the type and message Retrofit handed over.
 */
class PlanetApiFailureTest {

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

    /** Runs [call] on the production wiring over [okHttp] and returns the failure, as "Type: message". */
    private fun failure(okHttp: OkHttpClient, before: () -> Unit = {}, call: suspend PlanetApi.() -> Unit): String {
        val client = NetworkModule.provideKtorHttpClient(okHttp).also { ktorClients += it }
        val api = NetworkModule.providePlanetApi(client, NetworkModule.provideJson())
        before()
        return try {
            runBlocking { api.call() }
            "no failure"
        } catch (e: Exception) {
            "${e::class.java.name}: ${e.message}"
        }
    }

    /**
     * The call threw [expectedType], with [expectedMessage] when given. A read timeout's message is
     * not pinned: OkHttp itself words it either "timeout" or "Read timed out", depending on whether
     * okio's watchdog or the socket's own timeout fires first.
     */
    private fun assertFailure(expectedType: Class<out Exception>, failure: String, expectedMessage: String? = null) {
        assertEquals(failure, expectedType.name, failure.substringBefore(':'))
        if (expectedMessage != null) assertEquals("${expectedType.name}: $expectedMessage", failure)
    }

    @Test
    fun unknownHost_throwsUnknownHostException() {
        val failure = failure(OkHttpClient.Builder().dns { host -> throw UnknownHostException("no such host: $host") }.build()) {
            getJsonObject(null, "http://planet.invalid/db/_all_dbs")
        }

        assertFailure(UnknownHostException::class.java, failure, "no such host: planet.invalid")
    }

    @Test
    fun connectTimeout_throwsSocketTimeoutException() {
        val failure = failure(OkHttpClient.Builder().socketFactory(TimingOutSocketFactory).build()) {
            isPlanetAvailable(server.url("/db/_all_dbs").toString())
        }

        assertFailure(SocketTimeoutException::class.java, failure, "connect timed out")
    }

    @Test
    fun readTimeout_beforeHeaders_throwsSocketTimeoutException() {
        val failure = failure(
            OkHttpClient.Builder().readTimeout(200, TimeUnit.MILLISECONDS).build(),
            before = { server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.Stall).build()) },
        ) {
            getJsonObject(null, server.url("/db/users").toString())
        }

        assertFailure(SocketTimeoutException::class.java, failure)
    }

    @Test
    fun readTimeout_whileStreaming_throwsSocketTimeoutException() {
        val failure = failure(
            OkHttpClient.Builder().readTimeout(200, TimeUnit.MILLISECONDS).build(),
            before = {
                server.enqueue(MockResponse.Builder().setHeader("Content-Length", 1_000).onResponseBody(SocketEffect.Stall).build())
            },
        ) {
            requireNotNull(downloadFile(null, server.url("/big.bin").toString()).body).use { it.source().readByteArray() }
        }

        assertFailure(SocketTimeoutException::class.java, failure)
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
