package org.ole.planet.myplanet.data.api

import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.charset
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.encodedPath
import io.ktor.http.takeFrom
import io.ktor.utils.io.ClosedByteChannelException
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.charsets.forName
import io.ktor.utils.io.core.String
import io.ktor.utils.io.core.toByteArray
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okio.BufferedSource
import org.ole.planet.myplanet.model.ChatResponse
import org.ole.planet.myplanet.model.DocumentResponse
import org.ole.planet.myplanet.model.MyPlanet

/**
 * [PlanetApi] over a Ktor [HttpClient], reproducing [RetrofitPlanetApi] on the wire and in what
 * callers observe (pinned by `PlanetApiContractTest`):
 *
 * - the [client] must be built with `expectSuccess = false`, `followRedirects = false` and
 *   `useDefaultTransformers = false`, so Ktor adds no Accept/Accept-Charset headers and leaves
 *   status codes and redirects to the engine;
 * - every response type is decoded with the same [json] instance Retrofit's kotlinx converter
 *   uses, eagerly for typed bodies (a malformed body throws from the call, as with Retrofit) and
 *   lazily for text bodies and error bodies;
 * - Content-Type values go on the wire verbatim (never re-formatted by Ktor), and a text body
 *   gets the same `; charset=utf-8` OkHttp appends when its type names no charset;
 * - [downloadFile] returns as soon as the headers arrive and streams the body afterwards.
 *
 * Nothing here is JVM-specific: URL canonicalisation, files and blocking reads go through
 * [platform], and the engine-side half (redirects, User-Agent, the final request URL) lives in
 * [KtorHttpClients].
 */
class KtorPlanetApi(
    private val client: HttpClient,
    private val json: Json,
    private val platform: KtorPlatform,
) : PlanetApi {

    /** Owns the open download exchanges; a child of the client, so closing the client ends them. */
    private val streams = CoroutineScope(client.coroutineContext + SupervisorJob(client.coroutineContext[Job]))

    override suspend fun downloadFile(authorization: String?, url: String?, range: String?, ifRange: String?): ApiResponse<StreamBody> {
        val statement = client.prepareRequest {
            target(HttpMethod.Get, url)
            optionalHeader(HttpHeaders.Authorization, authorization)
            optionalHeader(HttpHeaders.Range, range)
            optionalHeader(HttpHeaders.IfRange, ifRange)
        }
        // HttpStatement.execute only keeps the body unread inside its block, so the block runs
        // in its own coroutine and parks there until the StreamBody is closed.
        val opened = CompletableDeferred<HttpResponse>()
        val exchange = streams.launch {
            try {
                statement.execute { response ->
                    opened.complete(response)
                    awaitCancellation()
                }
            } catch (e: Throwable) {
                opened.completeExceptionally(e)
            }
        }
        val response = try {
            opened.await()
        } catch (e: CancellationException) {
            exchange.cancel()
            throw e
        } catch (e: Throwable) {
            throw e.withoutEngineWrapper()
        }

        val head = Head(response)
        if (!head.hasBody) {
            // Retrofit buffers a streaming call's error body whole and drops a 204/205 body.
            val errorBytes = try {
                if (head.isSuccessful) null else response.bodyAsChannel().toByteArray()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                throw e.withoutEngineWrapper()
            } finally {
                exchange.cancel()
            }
            return head.toApiResponse(bodyReader = { null }, errorBytes = errorBytes)
        }
        val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
        val body = KtorStreamBody(contentLength, platform.bufferedSource(response.bodyAsChannel()), exchange)
        return head.toApiResponse(bodyReader = { body })
    }

    override suspend fun getDocuments(authorization: String?, url: String?): ApiResponse<DocumentResponse> =
        typed(DocumentResponse.serializer()) {
            target(HttpMethod.Get, url)
            optionalHeader(HttpHeaders.Authorization, authorization)
        }

    override suspend fun getJsonObject(authorization: String?, url: String?): ApiResponse<JsonObject> =
        typed(JsonObject.serializer()) {
            target(HttpMethod.Get, url)
            optionalHeader(HttpHeaders.Authorization, authorization)
        }

    override suspend fun postDoc(authorization: String?, contentType: String?, url: String?, body: JsonObject?): ApiResponse<JsonObject> =
        typed(JsonObject.serializer()) { jsonRequest(HttpMethod.Post, authorization, contentType, url, body) }

    override suspend fun postDocArray(authorization: String?, contentType: String?, url: String?, body: JsonObject?): ApiResponse<JsonArray> =
        typed(JsonArray.serializer()) { jsonRequest(HttpMethod.Post, authorization, contentType, url, body) }

    override suspend fun uploadResource(headers: Map<String, String>, url: String?, body: UploadBody?): ApiResponse<JsonObject> =
        typed(JsonObject.serializer()) {
            var overridingType: String? = null
            for ((name, value) in headers) {
                if (name.equals(HttpHeaders.ContentType, ignoreCase = true)) {
                    overridingType = strictMediaType(value)
                } else {
                    this.headers.append(name, value)
                }
            }
            target(HttpMethod.Put, url)
            uploadBody(body, overridingType)
        }

    override suspend fun putDoc(authorization: String?, contentType: String?, url: String?, body: JsonObject?): ApiResponse<JsonObject> =
        typed(JsonObject.serializer()) { jsonRequest(HttpMethod.Put, authorization, contentType, url, body) }

    override suspend fun checkVersion(url: String?): ApiResponse<MyPlanet> =
        typed(MyPlanet.serializer()) { target(HttpMethod.Get, url) }

    override suspend fun getApkVersion(url: String?): ApiResponse<String> = text(url)

    override suspend fun healthAccess(url: String?): ApiResponse<String> = text(url)

    override suspend fun getChecksum(url: String?): ApiResponse<String> = text(url)

    override suspend fun isPlanetAvailable(url: String?): ApiResponse<String> = text(url)

    override suspend fun chatGpt(url: String?, body: UploadBody?): ApiResponse<ChatResponse> =
        typed(ChatResponse.serializer()) {
            target(HttpMethod.Post, url)
            uploadBody(body, overridingType = null)
        }

    override suspend fun checkAiProviders(url: String?): ApiResponse<String> = text(url)

    override suspend fun getConfiguration(url: String?): ApiResponse<JsonObject> =
        typed(JsonObject.serializer()) { target(HttpMethod.Get, url) }

    // region exchange

    private suspend fun <T> typed(deserializer: DeserializationStrategy<T>, configure: HttpRequestBuilder.() -> Unit): ApiResponse<T> {
        val (head, bytes) = send(configure)
        // Retrofit converts a successful body inside the call, so a malformed one throws here too.
        val body = if (head.hasBody) json.decodeFromString(deserializer, bytes.decodeText(head.contentType)) else null
        return head.toApiResponse(bodyReader = { body }, errorBytes = bytes.takeUnless { head.isSuccessful })
    }

    private suspend fun text(url: String?): ApiResponse<String> {
        val (head, bytes) = send { target(HttpMethod.Get, url) }
        return head.toApiResponse(
            bodyReader = { if (head.hasBody) bytes.decodeText(head.contentType) else null },
            errorBytes = bytes.takeUnless { head.isSuccessful },
        )
    }

    /** A plain call: Ktor saves the whole body in memory before returning, as Retrofit does. */
    private suspend fun send(configure: HttpRequestBuilder.() -> Unit): Pair<Head, ByteArray> {
        val response = try {
            client.request(configure)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw e.withoutEngineWrapper()
        }
        return Head(response) to response.bodyAsChannel().toByteArray()
    }

    private class Head(response: HttpResponse) {
        val code: Int = response.status.value
        val message: String = response.status.description
        val contentType: String? = response.headers[HttpHeaders.ContentType]
        val requestUrl: String = response.headers[REQUEST_URL_HEADER] ?: response.call.request.url.toString()
        val headers: List<Pair<String, String>> = response.headers.entries()
            .filterNot { it.key.equals(REQUEST_URL_HEADER, ignoreCase = true) }
            .flatMap { (name, values) -> values.map { name to it } }

        val isSuccessful: Boolean get() = code in 200..299

        /** Retrofit hands a body to its converter only for a 2xx other than 204/205. */
        val hasBody: Boolean get() = isSuccessful && code != 204 && code != 205

        fun <T> toApiResponse(bodyReader: () -> T?, errorBytes: ByteArray? = null): ApiResponse<T> = ApiResponse(
            code = code,
            message = message,
            headers = headers,
            requestUrl = requestUrl,
            bodyReader = bodyReader,
            errorBodyReader = { errorBytes?.decodeText(contentType) },
        )
    }

    private class KtorStreamBody(
        override val contentLength: Long,
        private val source: BufferedSource,
        private val exchange: Job,
    ) : StreamBody {
        override fun source(): BufferedSource = source

        /** Ends the exchange; a partly read body cancels the call, as closing an OkHttp body does. */
        override fun close() {
            try {
                source.close()
            } finally {
                exchange.cancel()
            }
        }
    }

    // endregion

    // region request building

    /**
     * Points the request at [url] as OkHttp would send it. Ktor's own parser percent-encodes
     * characters OkHttp leaves alone (`[`, `]` in a path) and regroups repeated query keys, so
     * the canonical path and query are written back verbatim. One gap remains: a malformed escape
     * such as `%zz`, which OkHttp sends untouched, makes Ktor fail the call with URLDecodeException.
     */
    private fun HttpRequestBuilder.target(httpMethod: HttpMethod, url: String?) {
        method = httpMethod
        val absolute = requireNotNull(url) { "@Url parameter is null." }
        val canonical = requireNotNull(platform.canonicalUrl(absolute)) { "Malformed URL: $absolute" }
        val pathStart = canonical.indexOf('/', canonical.indexOf("://") + 3).let { if (it == -1) canonical.length else it }
        val queryStart = canonical.indexOf('?', pathStart).takeIf { it != -1 }
        val fragmentStart = canonical.indexOf('#', pathStart).takeIf { it != -1 } ?: canonical.length
        val query = queryStart?.let { canonical.substring(it + 1, fragmentStart) }
        this.url {
            takeFrom(canonical.substring(0, pathStart))
            encodedPath = canonical.substring(pathStart, queryStart ?: fragmentStart)
            encodedParameters.clear()
            // One key with no values is written as-is: the whole query, in OkHttp's order and encoding.
            if (!query.isNullOrEmpty()) encodedParameters.appendAll(query, emptyList())
            trailingQuery = query != null && query.isEmpty()
            // Never sent, but OkHttp keeps it in the request URL a response reports.
            encodedFragment = if (fragmentStart < canonical.length) canonical.substring(fragmentStart + 1) else ""
        }
    }

    private fun HttpRequestBuilder.optionalHeader(name: String, value: String?) {
        if (value != null) headers.append(name, value)
    }

    private fun HttpRequestBuilder.jsonRequest(httpMethod: HttpMethod, authorization: String?, contentType: String?, url: String?, body: JsonObject?) {
        optionalHeader(HttpHeaders.Authorization, authorization)
        val overridingType = contentType?.let(::strictMediaType)
        target(httpMethod, url)
        val document = requireNotNull(body) { "Body parameter value must not be null." }
        // Retrofit's kotlinx converter creates the body as "application/json" text, which OkHttp
        // labels "application/json; charset=utf-8"; a Content-Type argument replaces that label.
        rawBody(json.encodeToString(JsonObject.serializer(), document).encodeToByteArray(), overridingType ?: "$JSON_MEDIA_TYPE; charset=utf-8")
    }

    private fun HttpRequestBuilder.uploadBody(body: UploadBody?, overridingType: String?) {
        val upload = requireNotNull(body) { "Body parameter value must not be null." }
        val bodyType = upload.contentType?.takeIf(::isMediaType)
        when (upload) {
            is UploadBody.FileContent -> {
                setBody(platform.fileContent(upload.path))
                contentTypeHeader(overridingType ?: bodyType)
            }
            is UploadBody.TextContent -> {
                val (bytes, encodedType) = encodeText(upload.text, bodyType)
                rawBody(bytes, overridingType ?: encodedType)
            }
        }
    }

    /**
     * The Content-Type rides as a request header and the content itself carries none, so the
     * OkHttp engine forwards the string untouched instead of Ktor's re-formatted [ContentType].
     */
    private fun HttpRequestBuilder.rawBody(bytes: ByteArray, contentType: String?) {
        setBody(ByteArrayContent(bytes))
        contentTypeHeader(contentType)
    }

    private fun HttpRequestBuilder.contentTypeHeader(contentType: String?) {
        if (contentType != null) headers[HttpHeaders.ContentType] = contentType
    }

    // endregion

    companion object {
        /**
         * Response header the engine side sets to the URL actually requested, after redirects and
         * canonicalisation; Ktor's own request URL is the pre-redirect one. Never surfaced to callers.
         */
        const val REQUEST_URL_HEADER = "X-Planet-Request-Url"

        private const val JSON_MEDIA_TYPE = "application/json"

        private const val TOKEN = "[a-zA-Z0-9-!#$%&'*+.^_`{|}~]+"
        private val TYPE_SUBTYPE = Regex("$TOKEN/$TOKEN")
        private val PARAMETER = Regex(";\\s*(?:$TOKEN=(?:$TOKEN|'[^']*'|\"[^\"]*\"))?")

        /** OkHttp's `MediaType` grammar: what `toMediaTypeOrNull()` accepts. */
        private fun isMediaType(value: String): Boolean {
            var index = TYPE_SUBTYPE.matchAt(value, 0)?.range?.let { it.last + 1 } ?: return false
            while (index < value.length) {
                val parameter = PARAMETER.matchAt(value, index) ?: return false
                index = parameter.range.last + 1
            }
            return true
        }

        /** Retrofit parses a Content-Type header argument and rejects one OkHttp cannot read. */
        private fun strictMediaType(value: String): String {
            require(isMediaType(value)) { "Malformed content type: $value" }
            return value
        }

        private fun charsetOf(contentType: String): Charset? = try {
            ContentType.parse(contentType).parameter("charset")?.trim('\'')?.let { Charsets.forName(it) }
        } catch (_: Exception) {
            null
        }

        /** OkHttp's `String.toRequestBody`: the type's charset, or UTF-8 added to the type. */
        private fun encodeText(text: String, contentType: String?): Pair<ByteArray, String?> {
            if (contentType == null) return text.encodeToByteArray() to null
            val charset = charsetOf(contentType) ?: return text.encodeToByteArray() to "$contentType; charset=utf-8"
            return text.toByteArray(charset) to contentType
        }

        /** OkHttp's `ResponseBody.string()`: a UTF-8 BOM wins, then the Content-Type charset, then UTF-8. */
        private fun ByteArray.decodeText(contentType: String?): String {
            if (size >= 3 && this[0] == 0xEF.toByte() && this[1] == 0xBB.toByte() && this[2] == 0xBF.toByte()) {
                return decodeToString(3, size)
            }
            val charset = contentType?.let { type ->
                try {
                    ContentType.parse(type).charset()
                } catch (_: Exception) {
                    null
                }
            }
            return if (charset == null || charset == Charsets.UTF_8) decodeToString() else String(this, charset = charset)
        }
    }
}

/**
 * The exception OkHttp itself threw, as Retrofit would hand it over. The OkHttp engine re-wraps
 * socket timeouts — a connect timeout becomes Ktor's ConnectTimeoutException (a ConnectException,
 * no longer a SocketTimeoutException), a read timeout a new SocketTimeoutException with Ktor's
 * message — and a failure while streaming reaches the body reader inside one or more
 * ClosedByteChannelExceptions. Callers branch on these types, so the wrappers come off.
 */
internal fun Throwable.withoutEngineWrapper(): Throwable {
    var error = this
    while (error is ClosedByteChannelException) error = error.cause ?: break
    val original = error.cause
    return when {
        error is ConnectTimeoutException && original != null -> original
        error is SocketTimeoutException && original is SocketTimeoutException -> original
        else -> error
    }
}
