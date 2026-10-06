package org.ole.planet.myplanet.data.api

import io.ktor.client.HttpClient
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.ole.planet.myplanet.di.NetworkModule

/**
 * Sends every [PlanetApi] call through [RetrofitPlanetApi] and [KtorPlanetApi] and compares what
 * reached the server byte for byte: request line, every header (User-Agent, Accept-Encoding,
 * Connection and Host included — the contract test leaves those to the transport) and body.
 */
class PlanetApiWireParityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var retrofit: PlanetApi
    private lateinit var ktor: PlanetApi
    private lateinit var ktorClient: HttpClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val json = NetworkModule.provideJson()
        retrofit = NetworkModule.providePlanetApi(
            NetworkModule.provideApiInterface(NetworkModule.provideStandardRetrofit(OkHttpClient(), NetworkModule.provideGson(), json))
        )
        ktorClient = NetworkModule.provideKtorHttpClient(OkHttpClient())
        ktor = KtorPlanetApi(ktorClient, json, JvmKtorPlatform)
    }

    @After
    fun tearDown() {
        ktorClient.close()
        server.close()
    }

    private data class Wire(val requestLine: String, val headers: List<String>, val body: String)

    private fun RecordedRequest.wire() = Wire(
        requestLine,
        headers.map { (name, value) -> "${name.lowercase()}: $value" }.sorted(),
        body?.hex() ?: "",
    )

    private fun capture(api: PlanetApi, responseBody: String, call: suspend PlanetApi.() -> ApiResponse<*>): Wire {
        server.enqueue(MockResponse.Builder().setHeader("Content-Type", "application/json").body(responseBody).build())
        runBlocking { (api.call().body as? StreamBody)?.close() }
        return requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "no request reached the server" }.wire()
    }

    @Test
    fun everyEndpoint_putsIdenticalBytesOnTheWire() {
        val url = { path: String -> server.url(path).toString() }
        val doc = buildJsonObject {
            put("_id", "doc-1")
            put("text", "Ünïcødé ✓")
        }
        val file = tempFolder.newFile("photo.png").apply { writeBytes(ByteArray(10_000) { it.toByte() }) }
        val calls: List<Pair<String, suspend PlanetApi.() -> ApiResponse<*>>> = listOf(
            "downloadFile" to { downloadFile("Basic abc", url("/db/resources/r1/a file.pdf")) },
            "downloadFile resume" to { downloadFile("Basic abc", url("/db/resources/r1/a.pdf"), "bytes=10-", "\"etag\"") },
            "getDocuments" to { getDocuments("Basic abc", url("/db/shelf/_all_docs?include_docs=true")) },
            "getJsonObject" to { getJsonObject("", url("/db/users/_all_docs?limit=2&skip=4")) },
            "postDoc" to { postDoc("Basic abc", "application/json", url("/db/health"), doc) },
            "postDoc no type" to { postDoc(null, null, url("/db/health/_find"), doc) },
            "postDocArray" to { postDocArray("Basic abc", "application/json", url("/db/news/_bulk_docs"), doc) },
            "uploadResource file" to {
                uploadResource(
                    mapOf("Authorization" to "Basic abc", "Content-Type" to "image/png", "If-Match" to "1-a"),
                    url("/db/resources/r1/photo.png"),
                    UploadBody.FileContent(file.path, "application/octet-stream"),
                )
            },
            "uploadResource text" to { uploadResource(mapOf("Authorization" to "Basic abc"), url("/db/resources/r1/n.txt"), UploadBody.TextContent("héllo", "text/plain")) },
            "putDoc" to { putDoc("Basic abc", "application/json; charset=UTF-8", url("/db/_users/org.couchdb.user:alice"), doc) },
            "checkVersion" to { checkVersion(url("/versions")) },
            "getApkVersion" to { getApkVersion(url("/apkversion")) },
            "healthAccess" to { healthAccess(url("/healthaccess?p=1234")) },
            "getChecksum" to { getChecksum(url("/fs/myPlanet.apk.sha256")) },
            "isPlanetAvailable" to { isPlanetAvailable(url("/db/_all_dbs")) },
            "chatGpt" to { chatGpt(url("/"), UploadBody.TextContent("""{"content":"héllo"}""", "application/json")) },
            "checkAiProviders" to { checkAiProviders(url("/checkProviders/")) },
            "getConfiguration" to { getConfiguration(url("/db/configurations/_all_docs?include_docs=true")) },
        )

        for ((name, call) in calls) {
            val responseBody = if (name == "postDocArray") "[]" else "{}"
            assertEquals(name, capture(retrofit, responseBody, call), capture(ktor, responseBody, call))
        }
    }
}
