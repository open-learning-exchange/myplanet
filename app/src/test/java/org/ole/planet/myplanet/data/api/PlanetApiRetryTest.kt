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
 * Retry behaviour through [KtorPlanetApi] on the production OkHttp client
 * ([NetworkModule.provideStandardOkHttpClient] with the real [RetryInterceptor]), which Ktor
 * reuses as its engine. Each outcome — request count, request lines, bodies resent, final status
 * and body — is pinned whole, at what the Retrofit client it replaced produced.
 *
 * Backoff stays at production values; [TestTimeProvider] makes each sleep return at once.
 */
class PlanetApiRetryTest {

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

    private fun ktorApi(): PlanetApi {
        val client = NetworkModule.provideKtorHttpClient(productionOkHttp()).also { ktorClients += it }
        return NetworkModule.providePlanetApi(client, NetworkModule.provideJson())
    }

    /** Runs [call] against a fresh server primed with [responses] and returns what happened. */
    private fun outcome(responses: List<MockResponse>, call: suspend PlanetApi.(MockWebServer) -> ApiResponse<*>): Outcome {
        val server = MockWebServer().also { servers += it }
        responses.forEach(server::enqueue)
        server.start()
        val response = runBlocking { ktorApi().call(server) }
        val body = when (val value = response.body) {
            is StreamBody -> value.use { it.source().readUtf8() }
            else -> value?.toString()
        }
        return Outcome(drain(server), response.code, body, response.errorBody())
    }

    private fun drain(server: MockWebServer): List<String> = List(server.requestCount) {
        val request = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        "${request.requestLine} ${request.body?.utf8() ?: ""}".trim()
    }

    private fun status(code: Int, body: String = "") = MockResponse.Builder().code(code).body(body).build()

    private val doc: JsonObject = buildJsonObject { put("_id", "d1") }

    @Test
    fun allowlistedGet_retriesA503UntilItSucceeds() {
        val outcome = outcome(listOf(status(503, "busy"), status(503, "busy"), status(200, """{"ok":true}"""))) {
            getJsonObject("Basic abc", it.url("/db/users").toString())
        }

        assertEquals(Outcome(List(3) { "GET /db/users HTTP/1.1" }, 200, """{"ok":true}""", null), outcome)
    }

    @Test
    fun get_givesUpAfterThreeRetriesAndReturnsTheLast503() {
        val outcome = outcome(List(4) { status(503, "busy $it") } + status(200, "{}")) {
            getApkVersion(it.url("/apkversion").toString())
        }

        assertEquals(Outcome(List(4) { "GET /apkversion HTTP/1.1" }, 503, null, "busy 3"), outcome)
    }

    @Test
    fun writePost_isNotRetried() {
        val outcome = outcome(listOf(status(503, "busy"), status(201, """{"ok":true}"""))) {
            postDoc("Basic abc", "application/json", it.url("/db/health").toString(), doc)
        }

        assertEquals(Outcome(listOf("""POST /db/health HTTP/1.1 {"_id":"d1"}"""), 503, null, "busy"), outcome)
    }

    @Test
    fun readOnlyPost_isRetried() {
        val outcome = outcome(listOf(status(503), status(200, """{"docs":[]}"""))) {
            postDoc("Basic abc", "application/json", it.url("/db/news/_find").toString(), doc)
        }

        assertEquals(Outcome(List(2) { """POST /db/news/_find HTTP/1.1 {"_id":"d1"}""" }, 200, """{"docs":[]}""", null), outcome)
    }

    @Test
    fun fileUpload_isRetriedWithTheWholeFileResent() {
        val file = tempFolder.newFile("note.txt").apply { writeText("attachment bytes") }

        val outcome = outcome(listOf(status(503), status(201, """{"ok":true}"""))) {
            uploadResource(mapOf("Content-Type" to "text/plain"), it.url("/db/resources/r/note.txt").toString(), UploadBody.FileContent(file.path, null))
        }

        assertEquals(Outcome(List(2) { "PUT /db/resources/r/note.txt HTTP/1.1 attachment bytes" }, 201, """{"ok":true}""", null), outcome)
    }

    @Test
    fun download_isRetriedAndStillStreams() {
        val outcome = outcome(listOf(status(503), status(200, "payload"))) {
            downloadFile("Basic abc", it.url("/db/resources/r/f.pdf").toString())
        }

        assertEquals(Outcome(List(2) { "GET /db/resources/r/f.pdf HTTP/1.1" }, 200, "payload", null), outcome)
    }

    @Test
    fun droppedConnection_isRetriedLikeA503() {
        val outcome = outcome(listOf(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build(), status(200, "_users,"))) {
            isPlanetAvailable(it.url("/db/_all_dbs").toString())
        }

        // The first exchange dies before a response; the interceptor's retry gets the 200.
        // MockWebServer records the dropped exchange as "GET /": it closed before reading the request.
        assertEquals(Outcome(listOf("GET / HTTP/1.1", "GET /db/_all_dbs HTTP/1.1"), 200, "_users,", null), outcome)
    }

    @Test
    fun strayPercentDownload_isRetriedAtTheSameUrl() {
        val outcome = outcome(listOf(status(503), status(200, "payload"))) {
            downloadFile("Basic abc", it.url("/db").toString() + "/resources/r/100% guide.pdf")
        }

        assertEquals(Outcome(List(2) { "GET /db/resources/r/100%%20guide.pdf HTTP/1.1" }, 200, "payload", null), outcome)
    }
}
