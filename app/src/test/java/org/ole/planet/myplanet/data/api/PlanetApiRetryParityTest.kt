package org.ole.planet.myplanet.data.api

import io.ktor.client.HttpClient
import io.mockk.mockk
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.ole.planet.myplanet.di.NetworkModule
import org.ole.planet.myplanet.utils.TestTimeProvider

/**
 * Retry behaviour through both [PlanetApi] implementations on the production OkHttp client
 * ([NetworkModule.provideStandardOkHttpClient] with the real [RetryInterceptor]). Ktor reuses
 * that client as its engine, so the interceptor sees the same requests and the outcomes —
 * request counts, request lines, bodies resent, final status — must match exactly.
 *
 * Backoff stays at production values; [TestTimeProvider] makes each sleep return at once.
 */
class PlanetApiRetryParityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val servers = mutableListOf<MockWebServer>()
    private val ktorClients = mutableListOf<HttpClient>()

    @After
    fun tearDown() {
        ktorClients.forEach { it.close() }
        servers.forEach { it.close() }
    }

    private data class Outcome(val requests: List<String>, val code: Int, val body: String?, val errorBody: String?)

    private fun productionOkHttp(): OkHttpClient =
        NetworkModule.provideStandardOkHttpClient(RetryInterceptor(mockk(relaxed = true), TestTimeProvider()), ConnectionPool())

    private fun retrofitApi(): PlanetApi {
        val retrofit = NetworkModule.provideStandardRetrofit(productionOkHttp(), NetworkModule.provideGson(), NetworkModule.provideJson())
        return NetworkModule.providePlanetApi(NetworkModule.provideApiInterface(retrofit))
    }

    private fun ktorApi(): PlanetApi {
        val client = NetworkModule.provideKtorHttpClient(productionOkHttp()).also { ktorClients += it }
        return KtorPlanetApi(client, NetworkModule.provideJson(), JvmKtorPlatform)
    }

    /**
     * Runs [call] once per implementation, each against a fresh server primed with [responses],
     * and returns both outcomes (Retrofit first) after asserting they are identical.
     */
    private fun parity(responses: () -> List<MockResponse>, call: suspend PlanetApi.(MockWebServer) -> ApiResponse<*>): Outcome {
        val outcomes = listOf(retrofitApi(), ktorApi()).map { api ->
            val server = MockWebServer().also { servers += it }
            responses().forEach(server::enqueue)
            server.start()
            val response = runBlocking { api.call(server) }
            val body = when (val value = response.body) {
                is StreamBody -> value.use { it.source().readUtf8() }
                else -> value?.toString()
            }
            Outcome(drain(server), response.code, body, response.errorBody())
        }
        assertEquals("Retrofit vs Ktor", outcomes[0], outcomes[1])
        return outcomes[1]
    }

    private fun drain(server: MockWebServer): List<String> = List(server.requestCount) {
        val request = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        "${request.requestLine} ${request.body?.utf8() ?: ""}".trim()
    }

    private fun status(code: Int, body: String = "") = MockResponse.Builder().code(code).body(body).build()

    private val doc: JsonObject = buildJsonObject { put("_id", "d1") }

    @Test
    fun allowlistedGet_retriesA503UntilItSucceeds() {
        val outcome = parity({ listOf(status(503, "busy"), status(503, "busy"), status(200, """{"ok":true}""")) }) {
            getJsonObject("Basic abc", it.url("/db/users").toString())
        }

        assertEquals(List(3) { "GET /db/users HTTP/1.1" }, outcome.requests)
        assertEquals(200, outcome.code)
        assertEquals("""{"ok":true}""", outcome.body)
    }

    @Test
    fun get_givesUpAfterThreeRetriesAndReturnsTheLast503() {
        val outcome = parity({ List(4) { status(503, "busy $it") } + status(200, "{}") }) {
            getApkVersion(it.url("/apkversion").toString())
        }

        assertEquals(4, outcome.requests.size)
        assertEquals(503, outcome.code)
        assertEquals("busy 3", outcome.errorBody)
    }

    @Test
    fun writePost_isNotRetried() {
        val outcome = parity({ listOf(status(503, "busy"), status(201, """{"ok":true}""")) }) {
            postDoc("Basic abc", "application/json", it.url("/db/health").toString(), doc)
        }

        assertEquals(listOf("""POST /db/health HTTP/1.1 {"_id":"d1"}"""), outcome.requests)
        assertEquals(503, outcome.code)
        assertEquals("busy", outcome.errorBody)
    }

    @Test
    fun readOnlyPost_isRetried() {
        val outcome = parity({ listOf(status(503), status(200, """{"docs":[]}""")) }) {
            postDoc("Basic abc", "application/json", it.url("/db/news/_find").toString(), doc)
        }

        assertEquals(List(2) { """POST /db/news/_find HTTP/1.1 {"_id":"d1"}""" }, outcome.requests)
        assertEquals(200, outcome.code)
    }

    @Test
    fun fileUpload_isRetriedWithTheWholeFileResent() {
        val file = tempFolder.newFile("note.txt").apply { writeText("attachment bytes") }

        val outcome = parity({ listOf(status(503), status(201, """{"ok":true}""")) }) {
            uploadResource(mapOf("Content-Type" to "text/plain"), it.url("/db/resources/r/note.txt").toString(), UploadBody.FileContent(file.path, null))
        }

        assertEquals(List(2) { "PUT /db/resources/r/note.txt HTTP/1.1 attachment bytes" }, outcome.requests)
        assertEquals(201, outcome.code)
    }

    @Test
    fun download_isRetriedAndStillStreams() {
        val outcome = parity({ listOf(status(503), status(200, "payload")) }) {
            downloadFile("Basic abc", it.url("/db/resources/r/f.pdf").toString())
        }

        assertEquals(2, outcome.requests.size)
        assertEquals(200, outcome.code)
        assertEquals("payload", outcome.body)
    }

    @Test
    fun droppedConnection_isRetriedLikeA503() {
        val outcome = parity({ listOf(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build(), status(200, "_users,")) }) {
            isPlanetAvailable(it.url("/db/_all_dbs").toString())
        }

        // The first exchange dies before a response; the interceptor's retry gets the 200.
        assertEquals(2, outcome.requests.size)
        assertEquals(200, outcome.code)
        assertEquals("_users,", outcome.body)
    }
}
