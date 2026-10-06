package org.ole.planet.myplanet.data.api

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The wire contract every [PlanetApi] implementation must honour, pinned against a real HTTP
 * server: the request line, the headers that carry meaning (Authorization, Content-Type,
 * Range, If-Range, custom header maps), the exact body bytes, and how status codes, error
 * bodies, headers and streamed downloads come back through [ApiResponse].
 *
 * Transport-chosen headers (Host, User-Agent, Accept-Encoding, Connection) are not part of
 * the contract, but no other header may appear. URLs must reach the server as OkHttp
 * canonicalises them, and failures surface as the exception types callers branch on
 * (IOException, IllegalArgumentException, SerializationException). Subclasses only supply
 * [createApi]; [PlanetApiWireParityTest] compares the two implementations byte for byte.
 */
abstract class PlanetApiContractTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    protected lateinit var server: MockWebServer
    private lateinit var api: PlanetApi

    /** Builds the implementation under test, pointed at nothing in particular — every call passes an absolute URL. */
    protected abstract fun createApi(baseUrl: String): PlanetApi

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
        api = createApi(server.url("/").toString())
    }

    @After
    fun stopServer() {
        server.close()
    }

    private fun url(path: String): String = server.url(path).toString()

    private fun enqueueJson(json: String, code: Int = 200) {
        server.enqueue(
            MockResponse.Builder()
                .code(code)
                .setHeader("Content-Type", "application/json")
                .body(json)
                .build()
        )
    }

    private fun takeRequest(): RecordedRequest = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "no request reached the server" }

    private fun RecordedRequest.bodyUtf8(): String = body?.utf8() ?: ""

    /**
     * The request carries exactly [expected] beyond the transport's own Host, User-Agent,
     * Accept-Encoding and Connection — in particular no Accept or Accept-Charset.
     */
    private fun RecordedRequest.assertHeaderNames(vararg expected: String) {
        val transport = setOf("host", "user-agent", "accept-encoding", "connection")
        val actual = headers.names().map { it.lowercase() }.filterNot { it in transport }.toSortedSet()
        assertEquals(requestLine, expected.map { it.lowercase() }.toSortedSet(), actual)
    }

    private val doc: JsonObject = buildJsonObject {
        put("_id", "doc-1")
        put("count", 2)
        put("name", "Ünïcødé ✓")
    }
    private val docBytes = """{"_id":"doc-1","count":2,"name":"Ünïcødé ✓"}"""

    // region request shape

    @Test
    fun getJsonObject_sendsGetWithAuthorizationAndNoBody() = runBlocking {
        enqueueJson("""{"ok":true}""")

        api.getJsonObject("Basic dXNlcjpwYXNz", url("/db/users/_all_docs?include_docs=true&limit=2"))

        val request = takeRequest()
        assertEquals("GET /db/users/_all_docs?include_docs=true&limit=2 HTTP/1.1", request.requestLine)
        assertEquals("Basic dXNlcjpwYXNz", request.headers["Authorization"])
        assertNull(request.headers["Content-Type"])
        assertEquals(0L, request.bodySize)
    }

    @Test
    fun nullAuthorization_omitsTheHeader() = runBlocking {
        enqueueJson("""{"ok":true}""")

        api.getJsonObject(null, url("/db/users"))

        assertNull(takeRequest().headers["Authorization"])
    }

    @Test
    fun emptyAuthorization_isSentAsAnEmptyHeader() = runBlocking {
        enqueueJson("""{"ok":true}""")

        api.getJsonObject("", url("/db/communityregistrationrequests/_all_docs?include_docs=true"))

        val request = takeRequest()
        assertEquals(listOf(""), request.headers.values("Authorization"))
    }

    @Test
    fun getDocuments_sendsGet() = runBlocking {
        enqueueJson("""{"rows":[]}""")

        api.getDocuments("Basic abc", url("/db/shelf/_all_docs"))

        val request = takeRequest()
        assertEquals("GET /db/shelf/_all_docs HTTP/1.1", request.requestLine)
        assertEquals("Basic abc", request.headers["Authorization"])
    }

    @Test
    fun postDoc_sendsKotlinxEncodedBodyWithGivenContentType() = runBlocking {
        enqueueJson("""{"ok":true,"id":"doc-1","rev":"1-a"}""")

        api.postDoc("Basic abc", "application/json", url("/db/health"), doc)

        val request = takeRequest()
        assertEquals("POST /db/health HTTP/1.1", request.requestLine)
        assertEquals("Basic abc", request.headers["Authorization"])
        assertEquals("application/json", request.headers["Content-Type"])
        assertEquals(docBytes, request.bodyUtf8())
        assertEquals(docBytes.toByteArray(Charsets.UTF_8).size.toString(), request.headers["Content-Length"])
    }

    @Test
    fun postDoc_emptyObject_sendsEmptyJsonObject() = runBlocking {
        enqueueJson("""{"rows":[]}""")

        api.postDoc("Basic abc", "application/json", url("/db/news/_all_docs?include_docs=true&limit=10&skip=0"), JsonObject(emptyMap()))

        val request = takeRequest()
        assertEquals("POST /db/news/_all_docs?include_docs=true&limit=10&skip=0 HTTP/1.1", request.requestLine)
        assertEquals("{}", request.bodyUtf8())
    }

    @Test
    fun postDocArray_sendsPostAndDecodesArray() = runBlocking {
        enqueueJson("""[{"ok":true,"id":"a","rev":"1-x"},{"error":"conflict","id":"b"}]""", code = 201)

        val response = api.postDocArray("Basic abc", "application/json", url("/db/news/_bulk_docs"), doc)

        val request = takeRequest()
        assertEquals("POST /db/news/_bulk_docs HTTP/1.1", request.requestLine)
        assertEquals("application/json", request.headers["Content-Type"])
        assertEquals(docBytes, request.bodyUtf8())
        assertEquals(201, response.code)
        assertEquals(2, response.body?.size)
        assertEquals("conflict", response.body?.get(1)?.jsonObject?.get("error")?.jsonPrimitive?.content)
    }

    @Test
    fun putDoc_sendsPutWithBody() = runBlocking {
        enqueueJson("""{"ok":true,"id":"doc-1","rev":"2-b"}""", code = 201)

        val response = api.putDoc(null, "application/json", url("/db/_users/org.couchdb.user:alice"), doc)

        val request = takeRequest()
        assertEquals("PUT /db/_users/org.couchdb.user:alice HTTP/1.1", request.requestLine)
        assertNull(request.headers["Authorization"])
        assertEquals("application/json", request.headers["Content-Type"])
        assertEquals(docBytes, request.bodyUtf8())
        assertEquals("2-b", response.body?.get("rev")?.jsonPrimitive?.content)
    }

    @Test
    fun uploadResource_sendsFileBytesWithHeaderMap() = runBlocking {
        val bytes = ByteArray(70_000) { (it % 251).toByte() }
        val file = tempFolder.newFile("photo.png").apply { writeBytes(bytes) }
        enqueueJson("""{"ok":true,"id":"res-1","rev":"2-c"}""", code = 201)

        api.uploadResource(
            mapOf("Authorization" to "Basic abc", "Content-Type" to "image/png", "If-Match" to "1-abc"),
            url("/db/resources/res-1/photo.png"),
            UploadBody.FileContent(file.path, "application/octet-stream")
        )

        val request = takeRequest()
        assertEquals("PUT /db/resources/res-1/photo.png HTTP/1.1", request.requestLine)
        assertEquals("Basic abc", request.headers["Authorization"])
        assertEquals("image/png", request.headers["Content-Type"])
        assertEquals("1-abc", request.headers["If-Match"])
        assertEquals(bytes.size.toString(), request.headers["Content-Length"])
        assertArrayEquals(bytes, request.body?.toByteArray())
    }

    @Test
    fun uploadResource_withoutContentTypeHeader_usesTheBodyContentType() = runBlocking {
        val file = tempFolder.newFile("notes.txt").apply { writeText("hello") }
        enqueueJson("""{"ok":true}""", code = 201)

        api.uploadResource(mapOf("Authorization" to "Basic abc"), url("/db/resources/r/notes.txt"), UploadBody.FileContent(file.path, "text/plain"))

        val request = takeRequest()
        assertEquals("text/plain", request.headers["Content-Type"])
        assertEquals("hello", request.bodyUtf8())
    }

    @Test
    fun chatGpt_sendsTextBodyAsUtf8Json() = runBlocking {
        enqueueJson("""{"status":"Success","chat":"hi","couchDBResponse":{"ok":true,"id":"c1","rev":"1-r"}}""")
        val json = """{"data":{"user":"alice","content":"héllo"},"save":true}"""

        val response = api.chatGpt(url("/"), UploadBody.TextContent(json, "application/json"))

        val request = takeRequest()
        assertEquals("POST / HTTP/1.1", request.requestLine)
        assertEquals("application/json; charset=utf-8", request.headers["Content-Type"])
        assertArrayEquals(json.toByteArray(Charsets.UTF_8), request.body?.toByteArray())
        assertEquals("Success", response.body?.status)
        assertEquals("c1", response.body?.couchDBResponse?.id)
    }

    @Test
    fun plainGetEndpoints_sendGetWithoutAuthorization() = runBlocking {
        val calls: List<Pair<String, suspend (String) -> Unit>> = listOf(
            "/versions" to { u -> api.getConfiguration(u) },
            "/myplanet/versionInfo" to { u -> api.checkVersion(u) },
            "/apkversion" to { u -> api.getApkVersion(u) },
            "/healthaccess?p=1234" to { u -> api.healthAccess(u) },
            "/fs/myPlanet.apk.sha256" to { u -> api.getChecksum(u) },
            "/db/_all_dbs" to { u -> api.isPlanetAvailable(u) },
            "/checkProviders/" to { u -> api.checkAiProviders(u) },
        )
        for ((path, call) in calls) {
            enqueueJson("{}")
            call(url(path))
            val request = takeRequest()
            assertEquals("GET $path HTTP/1.1", request.requestLine)
            assertNull(path, request.headers["Authorization"])
            assertEquals(path, 0L, request.bodySize)
        }
    }

    @Test
    fun downloadFile_withoutResume_sendsNoRangeHeaders() = runBlocking {
        server.enqueue(MockResponse.Builder().body("data").build())

        api.downloadFile("Basic abc", url("/db/resources/r1/file.pdf")).body?.close()

        val request = takeRequest()
        assertEquals("GET /db/resources/r1/file.pdf HTTP/1.1", request.requestLine)
        assertEquals("Basic abc", request.headers["Authorization"])
        assertNull(request.headers["Range"])
        assertNull(request.headers["If-Range"])
    }

    @Test
    fun downloadFile_withResume_sendsRangeAndIfRange() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(206)
                .setHeader("ETag", "\"v2\"")
                .setHeader("Content-Range", "bytes 4-9/10")
                .body("456789")
                .build()
        )

        val response = api.downloadFile("Basic abc", url("/db/resources/r1/file.pdf"), "bytes=4-", "\"v2\"")

        val request = takeRequest()
        assertEquals("bytes=4-", request.headers["Range"])
        assertEquals("\"v2\"", request.headers["If-Range"])
        assertEquals(206, response.code)
        assertTrue(response.isSuccessful)
        assertEquals("\"v2\"", response.header("etag"))
        val body = requireNotNull(response.body)
        assertEquals(6L, body.contentLength)
        assertEquals("456789", body.source().use { it.readUtf8() })
    }

    @Test
    fun requests_carryNoHeadersBeyondTheContract() = runBlocking {
        val file = tempFolder.newFile("a.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val calls: List<Pair<suspend () -> Unit, Array<String>>> = listOf(
            suspend { api.getJsonObject("Basic abc", url("/db/a")); Unit } to arrayOf("Authorization"),
            suspend { api.getDocuments(null, url("/db/b")); Unit } to arrayOf(),
            suspend { api.postDoc("Basic abc", "application/json", url("/db/c"), doc); Unit } to arrayOf("Authorization", "Content-Type", "Content-Length"),
            suspend { api.putDoc(null, null, url("/db/d"), doc); Unit } to arrayOf("Content-Type", "Content-Length"),
            suspend {
                api.uploadResource(mapOf("Authorization" to "Basic abc", "If-Match" to "1-a"), url("/db/e/a.bin"), UploadBody.FileContent(file.path, null))
                Unit
            } to arrayOf("Authorization", "If-Match", "Content-Length"),
            suspend { api.chatGpt(url("/f"), UploadBody.TextContent("{}", "application/json")); Unit } to arrayOf("Content-Type", "Content-Length"),
            suspend { api.getChecksum(url("/g")); Unit } to arrayOf(),
            suspend { api.downloadFile("Basic abc", url("/h"), "bytes=1-", "\"e\"").body?.close(); Unit } to arrayOf("Authorization", "Range", "If-Range"),
        )
        for ((call, expected) in calls) {
            enqueueJson("{}")
            call()
            takeRequest().assertHeaderNames(*expected)
        }
    }

    @Test
    fun postDoc_withoutContentType_labelsTheBodyAsUtf8Json() = runBlocking {
        enqueueJson("""{"ok":true}""")

        api.postDoc("Basic abc", null, url("/db/health"), doc)

        val request = takeRequest()
        assertEquals("application/json; charset=utf-8", request.headers["Content-Type"])
        assertEquals(docBytes, request.bodyUtf8())
    }

    @Test
    fun contentTypeArgument_isSentVerbatim() = runBlocking {
        enqueueJson("""{"ok":true}""")

        api.putDoc(null, "application/json;charset=UTF-8", url("/db/health/h1"), doc)

        assertEquals("application/json;charset=UTF-8", takeRequest().headers["Content-Type"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedContentTypeArgument_throwsIllegalArgument() {
        runBlocking { api.postDoc(null, "not a media type", url("/db/x"), doc) }
    }

    @Test
    fun textBody_withCharset_isEncodedInThatCharsetAndLabelledVerbatim() = runBlocking {
        enqueueJson("""{"status":"Success"}""")

        api.chatGpt(url("/"), UploadBody.TextContent("café", "text/plain;charset=ISO-8859-1"))

        val request = takeRequest()
        assertEquals("text/plain;charset=ISO-8859-1", request.headers["Content-Type"])
        assertArrayEquals(byteArrayOf(0x63, 0x61, 0x66, 0xE9.toByte()), request.body?.toByteArray())
    }

    @Test
    fun textBody_withoutContentType_isUtf8WithNoContentType() = runBlocking {
        enqueueJson("""{"status":"Success"}""")

        api.chatGpt(url("/"), UploadBody.TextContent("é", null))

        val request = takeRequest()
        assertNull(request.headers["Content-Type"])
        assertArrayEquals("é".toByteArray(Charsets.UTF_8), request.body?.toByteArray())
    }

    @Test
    fun url_isCanonicalisedAsOkHttpDoes() = runBlocking {
        val base = url("/").trimEnd('/')
        val raws = listOf(
            "$base/db/resources/r1/my file ü+[1].pdf?key=\"a b\"&range=[1,{}]|^`&plus=a+b&pct=%41",
            "$base/db/_find?b=2&a=1&b=3&flag&empty=",
            "$base/db/x?",
            "$base/db/y#fragment",
            base.replace("://", "://satellite:p%40ss@") + "/db/_all_dbs",
        )
        for (raw in raws) {
            enqueueJson("{}")

            val response = api.getJsonObject(null, raw)

            val canonical = raw.toHttpUrl()
            val query = canonical.encodedQuery?.let { "?$it" } ?: ""
            val request = takeRequest()
            assertEquals(raw, "GET ${canonical.encodedPath}$query HTTP/1.1", request.requestLine)
            assertNull(raw, request.headers["Authorization"])
            assertEquals(raw, canonical.toString(), response.requestUrl)
        }
    }

    /**
     * `UrlUtils.getUrl(id, file, base)` appends a resource's raw file name, so whatever a name
     * holds — a bare `%`, a malformed escape, spaces, any script, `#` or `?` — must download
     * from where OkHttp sends it rather than fail the call.
     */
    @Test
    fun resourceFileNames_reachTheServerAsOkHttpSendsThem() = runBlocking {
        val base = url("/db").trimEnd('/')
        val expectedTargets = linkedMapOf(
            "100% guide.pdf" to "/db/resources/r1/100%%20guide.pdf",
            "50%off.mp4" to "/db/resources/r1/50%off.mp4",
            "a%zzb.txt" to "/db/resources/r1/a%zzb.txt",
            "trailing%" to "/db/resources/r1/trailing%",
            "%" to "/db/resources/r1/%",
            "%ff raw byte.bin" to "/db/resources/r1/%ff%20raw%20byte.bin",
            "already%20encoded.pdf" to "/db/resources/r1/already%20encoded.pdf",
            "my notes (v2).pdf" to "/db/resources/r1/my%20notes%20(v2).pdf",
            "résumé.pdf" to "/db/resources/r1/r%C3%A9sum%C3%A9.pdf",
            "كتاب الرياضيات.pdf" to "/db/resources/r1/%D9%83%D8%AA%D8%A7%D8%A8%20%D8%A7%D9%84%D8%B1%D9%8A%D8%A7%D8%B6%D9%8A%D8%A7%D8%AA.pdf",
            "पुस्तक.pdf" to "/db/resources/r1/%E0%A4%AA%E0%A5%81%E0%A4%B8%E0%A5%8D%E0%A4%A4%E0%A4%95.pdf",
            "a#b.pdf" to "/db/resources/r1/a",
            "what?.pdf" to "/db/resources/r1/what?.pdf",
            "50% off?x=1%zz&y=é#frag%zz é" to "/db/resources/r1/50%%20off?x=1%zz&y=%C3%A9",
            "100%#ü.pdf" to "/db/resources/r1/100%",
        )
        for ((fileName, target) in expectedTargets) {
            // Exactly what UrlUtils.getUrl("r1", fileName, base) builds.
            val raw = "$base/resources/r1/$fileName"
            server.enqueue(MockResponse.Builder().body("data").build())

            val response = api.downloadFile("Basic abc", raw)

            val body = requireNotNull(response.body) { fileName }
            assertEquals(fileName, "data", body.source().readUtf8())
            body.close()
            val request = takeRequest()
            assertEquals(fileName, "GET $target HTTP/1.1", request.requestLine)
            assertEquals(fileName, "Basic abc", request.headers["Authorization"])
            request.assertHeaderNames("Authorization")
            assertEquals(fileName, 200, response.code)
            assertEquals(fileName, raw.toHttpUrl().toString(), response.requestUrl)
        }
    }

    @Test
    fun strayPercent_inEveryEndpointKind_isSentAsOkHttpSendsIt() = runBlocking {
        val raw = url("/db") + "/resources/r1/100% guide.pdf?rev=50%off"
        val target = "/db/resources/r1/100%%20guide.pdf?rev=50%off"

        enqueueJson("{}")
        api.getJsonObject("Basic abc", raw)
        assertEquals("GET $target HTTP/1.1", takeRequest().requestLine)

        enqueueJson("{}")
        api.putDoc("Basic abc", "application/json", raw, doc)
        val put = takeRequest()
        assertEquals("PUT $target HTTP/1.1", put.requestLine)
        assertEquals(docBytes, put.bodyUtf8())
        put.assertHeaderNames("Authorization", "Content-Type", "Content-Length")

        enqueueJson("{}")
        api.uploadResource(mapOf("Authorization" to "Basic abc"), raw, UploadBody.TextContent("x", "text/plain"))
        val upload = takeRequest()
        assertEquals("PUT $target HTTP/1.1", upload.requestLine)
        upload.assertHeaderNames("Authorization", "Content-Type", "Content-Length")

        server.enqueue(MockResponse.Builder().body("ok").build())
        val text = api.healthAccess(raw)
        assertEquals("GET $target HTTP/1.1", takeRequest().requestLine)
        assertEquals("ok", text.body)
        assertEquals(raw.toHttpUrl().toString(), text.requestUrl)
    }

    @Test
    fun strayPercent_survivesARedirect() = runBlocking {
        server.enqueue(MockResponse.Builder().code(302).setHeader("Location", "/db/final/50%off.pdf").build())
        server.enqueue(MockResponse.Builder().body("data").build())

        val response = api.downloadFile(null, url("/db") + "/resources/r1/100% guide.pdf")
        response.body?.close()

        assertEquals("GET /db/resources/r1/100%%20guide.pdf HTTP/1.1", takeRequest().requestLine)
        assertEquals("GET /db/final/50%off.pdf HTTP/1.1", takeRequest().requestLine)
        assertEquals(url("/db") + "/final/50%off.pdf", response.requestUrl)
    }

    @Test
    fun redirect_isFollowedAndRequestUrlIsTheFinalUrl() = runBlocking {
        server.enqueue(MockResponse.Builder().code(302).setHeader("Location", "/db/final?x=1").build())
        enqueueJson("""{"ok":true}""")

        val response = api.getJsonObject("Basic abc", url("/db/start"))

        assertEquals(200, response.code)
        assertEquals(url("/db/final?x=1"), response.requestUrl)
        assertEquals("GET /db/start HTTP/1.1", takeRequest().requestLine)
        val followed = takeRequest()
        assertEquals("GET /db/final?x=1 HTTP/1.1", followed.requestLine)
        assertEquals("Basic abc", followed.headers["Authorization"])
    }

    // endregion

    // region response mapping

    @Test
    fun success_mapsCodeMessageBodyHeadersAndRequestUrl() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .status("HTTP/1.1 200 OK")
                .setHeader("Content-Type", "application/json")
                .addHeader("X-Multi", "first")
                .addHeader("X-Multi", "last")
                .body("""{"_id":"a","extra":{"ignored":true}}""")
                .build()
        )
        val target = url("/db/users/a?x=1")

        val response = api.getJsonObject(null, target)

        assertEquals(200, response.code)
        assertEquals("OK", response.message)
        assertTrue(response.isSuccessful)
        assertEquals("a", response.body?.get("_id")?.jsonPrimitive?.content)
        assertEquals("last", response.header("x-multi"))
        assertNull(response.header("ETag"))
        assertEquals(target, response.requestUrl)
    }

    @Test
    fun typedBody_isDecodedLeniently() = runBlocking {
        enqueueJson("""{"planetVersion":"0.20.1","minapkcode":null,"latestapkcode":7064,"unknownKey":[1,2],"appname":myPlanet}""")

        val response = api.checkVersion(url("/versions"))

        val planet = requireNotNull(response.body)
        assertEquals("0.20.1", planet.planetVersion)
        assertEquals(0, planet.minapkcode)
        assertEquals(7064, planet.latestapkcode)
        assertEquals("myPlanet", planet.appname)
    }

    @Test
    fun documentResponse_isDecoded() = runBlocking {
        enqueueJson("""{"total_rows":2,"offset":0,"rows":[{"id":"a","key":"a","value":{"rev":"1"}},{"id":"b","key":"b"}]}""")

        val response = api.getDocuments(null, url("/db/shelf/_all_docs"))

        assertEquals(listOf("a", "b"), response.body?.rows?.map { it.id })
    }

    @Test
    fun textEndpoint_returnsWholeBodyAsString() = runBlocking {
        server.enqueue(MockResponse.Builder().body("_users,courses,resources,").build())

        val response = api.isPlanetAvailable(url("/db/_all_dbs"))

        assertEquals("_users,courses,resources,", response.body)
        assertNull(response.errorBody())
    }

    @Test
    fun noContent_hasNullBody() = runBlocking {
        server.enqueue(MockResponse.Builder().code(204).build())

        val response = api.healthAccess(url("/healthaccess"))

        assertEquals(204, response.code)
        assertTrue(response.isSuccessful)
        assertNull(response.body)
    }

    @Test
    fun clientError_hasNullBodyAndReadableErrorBody() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .status("HTTP/1.1 404 Object Not Found")
                .setHeader("Content-Type", "application/json")
                .body("""{"error":"not_found","reason":"missing"}""")
                .build()
        )

        val response = api.getJsonObject("Basic abc", url("/db/users/missing"))

        assertEquals(404, response.code)
        assertEquals("Object Not Found", response.message)
        assertFalse(response.isSuccessful)
        assertNull(response.body)
        assertEquals("""{"error":"not_found","reason":"missing"}""", response.errorBody())
    }

    @Test
    fun conflict_onPut_isReturnedNotThrown() = runBlocking {
        enqueueJson("""{"error":"conflict","reason":"Document update conflict."}""", code = 409)

        val response = api.putDoc("Basic abc", "application/json", url("/db/notifications/n1"), doc)

        assertEquals(409, response.code)
        assertNull(response.body)
        assertEquals("""{"error":"conflict","reason":"Document update conflict."}""", response.errorBody())
    }

    @Test
    fun serverError_isReturnedWithErrorBody() = runBlocking {
        server.enqueue(MockResponse.Builder().code(503).body("down for maintenance").build())

        val response = api.getApkVersion(url("/apkversion"))

        assertEquals(503, response.code)
        assertFalse(response.isSuccessful)
        assertNull(response.body)
        assertEquals("down for maintenance", response.errorBody())
    }

    @Test
    fun downloadError_carriesCodeAndRequestUrl() = runBlocking {
        server.enqueue(MockResponse.Builder().code(416).body("bad range").build())
        val target = url("/db/resources/r1/file.pdf")

        val response = api.downloadFile("Basic abc", target, "bytes=99-", null)

        assertEquals(416, response.code)
        assertFalse(response.isSuccessful)
        assertNull(response.body)
        assertEquals(target, response.requestUrl)
    }

    @Test
    fun downloadFile_returnsBeforeTheBodyArrives() = runBlocking {
        // The server announces 1 MiB and then stalls: only a streaming implementation can return.
        server.enqueue(
            MockResponse.Builder()
                .setHeader("Content-Length", 1_048_576)
                .onResponseBody(SocketEffect.Stall)
                .build()
        )

        val response = withTimeout(5_000) { api.downloadFile(null, url("/big.bin")) }

        val body = requireNotNull(response.body)
        assertEquals(1_048_576L, body.contentLength)
        body.close()
    }

    @Test
    fun downloadFile_streamsLargeBodyByteForByte() = runBlocking {
        val bytes = ByteArray(3 * 1024 * 1024) { (it * 31 % 256).toByte() }
        val file = File(tempFolder.root, "out.bin")
        server.enqueue(MockResponse.Builder().body(Buffer().write(bytes)).build())

        val response = api.downloadFile(null, url("/big.bin"))

        requireNotNull(response.body).use { body ->
            file.outputStream().use { out -> body.source().inputStream().copyTo(out) }
        }
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test
    fun errorBody_isDecodedWithTheResponseCharset() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(400)
                .setHeader("Content-Type", "text/plain; charset=iso-8859-1")
                .body(Buffer().write(byteArrayOf(0x63, 0x61, 0x66, 0xE9.toByte())))
                .build()
        )

        assertEquals("café", api.getApkVersion(url("/apkversion")).errorBody())
    }

    @Test(expected = SerializationException::class)
    fun malformedTypedBody_throwsFromTheCall() {
        enqueueJson("""[1,2]""")
        runBlocking { api.getJsonObject(null, url("/db/users")) }
    }

    @Test
    fun downloadFile_readToTheEndAndClosed_releasesTheConnectionForReuse() = runBlocking {
        server.enqueue(MockResponse.Builder().body("data").build())
        enqueueJson("{}")

        val body = requireNotNull(api.downloadFile(null, url("/a.bin")).body)
        assertEquals("data", body.source().readUtf8())
        body.close()
        api.getJsonObject(null, url("/next"))

        val first = takeRequest()
        val second = takeRequest()
        assertEquals(first.connectionIndex, second.connectionIndex)
    }

    @Test
    fun downloadFile_closedHalfway_doesNotBlockLaterCalls() = runBlocking {
        server.enqueue(MockResponse.Builder().body(Buffer().write(ByteArray(2 * 1024 * 1024))).build())
        enqueueJson("""{"ok":true}""")

        val body = requireNotNull(api.downloadFile(null, url("/big.bin")).body)
        assertEquals(16, body.source().readByteArray(16).size)
        body.close()

        val next = withTimeout(5_000) { api.getJsonObject(null, url("/next")) }
        assertEquals(200, next.code)
    }

    @Test
    fun unreachableServer_throwsIOException() = runBlocking {
        val closed = MockWebServer().apply { start() }
        val target = closed.url("/db/_all_dbs").toString()
        closed.close()

        try {
            api.isPlanetAvailable(target)
            fail("expected an IOException")
        } catch (e: IOException) {
            // expected: callers catch IOException for network failures
        }
    }

    @Test
    fun missingUploadFile_throwsIOException() = runBlocking {
        val missing = File(tempFolder.root, "gone.png")
        enqueueJson("{}")

        try {
            api.uploadResource(mapOf("Content-Type" to "image/png"), url("/db/resources/r/gone.png"), UploadBody.FileContent(missing.path, null))
            fail("expected an IOException")
        } catch (e: IOException) {
            // expected
        }
    }

    // endregion

    // region argument validation

    @Test(expected = IllegalArgumentException::class)
    fun nullUrl_throwsIllegalArgument() {
        runBlocking { api.getJsonObject("Basic abc", null) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonAsciiHeaderValue_throwsIllegalArgument() {
        runBlocking { api.getJsonObject("Basic ü", url("/db/x")) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun nullUploadBody_throwsIllegalArgument() {
        runBlocking { api.uploadResource(emptyMap(), url("/db/x"), null) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun nullJsonBody_throwsIllegalArgument() {
        runBlocking { api.postDoc("Basic abc", "application/json", url("/db/x"), null) }
    }

    // endregion
}
