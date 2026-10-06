package org.ole.planet.myplanet.data.api

import io.ktor.client.HttpClient
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttp
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.ole.planet.myplanet.di.NetworkModule

/**
 * Sends every [PlanetApi] call through the production [KtorPlanetApi] wiring and pins what reached
 * the server byte for byte: request line, every header (User-Agent, Accept-Encoding, Connection
 * and Host included — the contract test leaves those to the transport) and body. The expected
 * values are what the Retrofit client it replaced put on the wire.
 */
class PlanetApiWireTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var api: PlanetApi
    private lateinit var client: HttpClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = NetworkModule.provideKtorHttpClient(OkHttpClient())
        api = NetworkModule.providePlanetApi(client, NetworkModule.provideJson())
    }

    @After
    fun tearDown() {
        client.close()
        server.close()
    }

    private data class Wire(val requestLine: String, val headers: List<String>, val body: String)

    private fun RecordedRequest.wire() = Wire(
        requestLine,
        headers.map { (name, value) -> "${name.lowercase()}: $value" }.sorted(),
        body?.hex() ?: "",
    )

    private fun capture(responseBody: String, call: suspend PlanetApi.() -> ApiResponse<*>): Wire {
        server.enqueue(MockResponse.Builder().setHeader("Content-Type", "application/json").body(responseBody).build())
        runBlocking { (api.call().body as? StreamBody)?.close() }
        return requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "no request reached the server" }.wire()
    }

    /** [requestLine] with OkHttp's own headers ([gzip] unless a Range asks for raw bytes) plus [extra]. */
    private fun wire(requestLine: String, vararg extra: String, body: String = "", gzip: Boolean = true): Wire {
        val transport = listOf(
            "connection: Keep-Alive",
            "host: ${server.hostName}:${server.port}",
            "user-agent: okhttp/${OkHttp.VERSION}",
        ) + if (gzip) listOf("accept-encoding: gzip") else emptyList()
        return Wire(requestLine, (transport + extra).sorted(), body)
    }

    @Test
    fun everyEndpoint_putsTheExpectedBytesOnTheWire() {
        val url = { path: String -> server.url(path).toString() }
        val doc = buildJsonObject {
            put("_id", "doc-1")
            put("text", "Ünïcødé ✓")
        }
        val docHex = """{"_id":"doc-1","text":"Ünïcødé ✓"}""".encodeUtf8().hex()
        val fileBytes = ByteArray(10_000) { it.toByte() }
        val file = tempFolder.newFile("photo.png").apply { writeBytes(fileBytes) }
        val calls: List<Triple<String, suspend PlanetApi.() -> ApiResponse<*>, Wire>> = listOf(
            Triple(
                "downloadFile",
                { downloadFile("Basic abc", url("/db/resources/r1/a file.pdf")) },
                wire("GET /db/resources/r1/a%20file.pdf HTTP/1.1", "authorization: Basic abc"),
            ),
            Triple(
                "downloadFile resume",
                { downloadFile("Basic abc", url("/db/resources/r1/a.pdf"), "bytes=10-", "\"etag\"") },
                wire("GET /db/resources/r1/a.pdf HTTP/1.1", "authorization: Basic abc", "if-range: \"etag\"", "range: bytes=10-", gzip = false),
            ),
            Triple(
                "getDocuments",
                { getDocuments("Basic abc", url("/db/shelf/_all_docs?include_docs=true")) },
                wire("GET /db/shelf/_all_docs?include_docs=true HTTP/1.1", "authorization: Basic abc"),
            ),
            Triple(
                "getJsonObject",
                { getJsonObject("", url("/db/users/_all_docs?limit=2&skip=4")) },
                wire("GET /db/users/_all_docs?limit=2&skip=4 HTTP/1.1", "authorization: "),
            ),
            Triple(
                "postDoc",
                { postDoc("Basic abc", "application/json", url("/db/health"), doc) },
                wire("POST /db/health HTTP/1.1", "authorization: Basic abc", "content-length: 40", "content-type: application/json", body = docHex),
            ),
            Triple(
                "postDoc no type",
                { postDoc(null, null, url("/db/health/_find"), doc) },
                wire("POST /db/health/_find HTTP/1.1", "content-length: 40", "content-type: application/json; charset=utf-8", body = docHex),
            ),
            Triple(
                "postDocArray",
                { postDocArray("Basic abc", "application/json", url("/db/news/_bulk_docs"), doc) },
                wire("POST /db/news/_bulk_docs HTTP/1.1", "authorization: Basic abc", "content-length: 40", "content-type: application/json", body = docHex),
            ),
            Triple(
                "uploadResource file",
                {
                    uploadResource(
                        mapOf("Authorization" to "Basic abc", "Content-Type" to "image/png", "If-Match" to "1-a"),
                        url("/db/resources/r1/photo.png"),
                        UploadBody.FileContent(file.path, "application/octet-stream"),
                    )
                },
                wire(
                    "PUT /db/resources/r1/photo.png HTTP/1.1",
                    "authorization: Basic abc", "content-length: 10000", "content-type: image/png", "if-match: 1-a",
                    body = fileBytes.toByteString().hex(),
                ),
            ),
            Triple(
                "uploadResource text",
                { uploadResource(mapOf("Authorization" to "Basic abc"), url("/db/resources/r1/n.txt"), UploadBody.TextContent("héllo", "text/plain")) },
                wire(
                    "PUT /db/resources/r1/n.txt HTTP/1.1",
                    "authorization: Basic abc", "content-length: 6", "content-type: text/plain; charset=utf-8",
                    body = "héllo".encodeUtf8().hex(),
                ),
            ),
            Triple(
                "putDoc",
                { putDoc("Basic abc", "application/json; charset=UTF-8", url("/db/_users/org.couchdb.user:alice"), doc) },
                wire(
                    "PUT /db/_users/org.couchdb.user:alice HTTP/1.1",
                    "authorization: Basic abc", "content-length: 40", "content-type: application/json; charset=UTF-8",
                    body = docHex,
                ),
            ),
            Triple("checkVersion", { checkVersion(url("/versions")) }, wire("GET /versions HTTP/1.1")),
            Triple("getApkVersion", { getApkVersion(url("/apkversion")) }, wire("GET /apkversion HTTP/1.1")),
            Triple("healthAccess", { healthAccess(url("/healthaccess?p=1234")) }, wire("GET /healthaccess?p=1234 HTTP/1.1")),
            Triple("getChecksum", { getChecksum(url("/fs/myPlanet.apk.sha256")) }, wire("GET /fs/myPlanet.apk.sha256 HTTP/1.1")),
            Triple("isPlanetAvailable", { isPlanetAvailable(url("/db/_all_dbs")) }, wire("GET /db/_all_dbs HTTP/1.1")),
            Triple(
                "chatGpt",
                { chatGpt(url("/"), UploadBody.TextContent("""{"content":"héllo"}""", "application/json")) },
                wire(
                    "POST / HTTP/1.1",
                    "content-length: 20", "content-type: application/json; charset=utf-8",
                    body = """{"content":"héllo"}""".encodeUtf8().hex(),
                ),
            ),
            Triple("checkAiProviders", { checkAiProviders(url("/checkProviders/")) }, wire("GET /checkProviders/ HTTP/1.1")),
            Triple(
                "getConfiguration",
                { getConfiguration(url("/db/configurations/_all_docs?include_docs=true")) },
                wire("GET /db/configurations/_all_docs?include_docs=true HTTP/1.1"),
            ),
        )

        for ((name, call, expected) in calls) {
            val responseBody = if (name == "postDocArray") "[]" else "{}"
            assertEquals(name, expected, capture(responseBody, call))
        }
    }

    /**
     * An unconfigured server leaves `UrlUtils` building `/db/...`, `://:5000/...` and the like;
     * Retrofit resolved those against its base URL, and they still go there (and fail with
     * Retrofit's message when even that cannot parse). An interceptor answers in place of the
     * network, recording the URL an app interceptor sees.
     */
    @Test
    fun relativeAndUnparseableUrls_resolveOrFailAsRetrofitDid() {
        val seen = mutableListOf<String>()
        val okHttp = OkHttpClient.Builder().addInterceptor { chain ->
            seen += chain.request().url.toString()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val offlineClient = NetworkModule.provideKtorHttpClient(okHttp)
        val offlineApi = NetworkModule.providePlanetApi(offlineClient, NetworkModule.provideJson())
        val sentTo = linkedMapOf(
            "" to "https://vi.media.mit.edu/",
            "/db" to "https://vi.media.mit.edu/db",
            "/db/resources/r1/100% guide.pdf" to "https://vi.media.mit.edu/db/resources/r1/100%%20guide.pdf",
            "/db/_users/org.couchdb.user:alice?rev=1" to "https://vi.media.mit.edu/db/_users/org.couchdb.user:alice?rev=1",
            "db/x" to "https://vi.media.mit.edu/db/x",
            "?x=1" to "https://vi.media.mit.edu/?x=1",
            "#f" to "https://vi.media.mit.edu/#f",
            "://:5000/" to "https://vi.media.mit.edu/://:5000/",
            "://:5000/checkProviders/" to "https://vi.media.mit.edu/://:5000/checkProviders/",
            "/versions" to "https://vi.media.mit.edu/versions",
            "//other.host/db" to "https://other.host/db",
        )
        val malformed = listOf("http://", "http://host:99999/db", "ftp://host/db")
        try {
            for (url in sentTo.keys + malformed) {
                seen.clear()
                val outcome = try {
                    val response = runBlocking { offlineApi.getJsonObject(null, url) }
                    "${response.code} ${response.requestUrl} via $seen"
                } catch (e: Exception) {
                    "${e::class.java.name}: ${e.message} via $seen"
                }
                val expected = sentTo[url]?.let { "200 $it via [$it]" }
                    ?: "java.lang.IllegalArgumentException: Malformed URL. Base: https://vi.media.mit.edu/, Relative: $url via []"
                assertEquals(url, expected, outcome)
            }
        } finally {
            offlineClient.close()
        }
    }
}
