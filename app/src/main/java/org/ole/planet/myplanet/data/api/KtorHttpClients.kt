package org.ole.planet.myplanet.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlin.io.encoding.Base64
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/** Builds the Ktor [HttpClient] [KtorPlanetApi] needs, on top of an existing [OkHttpClient]. */
object KtorHttpClients {
    /** What Ktor's engines send when a request names no User-Agent (`KTOR_DEFAULT_USER_AGENT`, internal API). */
    private const val KTOR_USER_AGENT = "ktor-client"

    /**
     * Wraps [okHttpClient] (same interceptors, timeouts, socket factory, dispatcher and connection
     * pool) so a request behaves as it does through Retrofit, and switches off everything Ktor
     * would otherwise add or decide on its own:
     *
     * - `expectSuccess = false`: non-2xx responses are returned, never thrown;
     * - `followRedirects = false`: redirects stay with OkHttp, inside the retry interceptor, as
     *   with Retrofit — and the OkHttp engine's own default of not following them is undone;
     * - `useDefaultTransformers = false`: no default `Accept` and no `Accept-Charset` header;
     * - Ktor's `User-Agent: ktor-client` is dropped so OkHttp sends its own, as with Retrofit;
     * - a URL Ktor could only carry escaped is swapped back to the exact one.
     *
     * Both happen in an interceptor ahead of the app's own (the retry interceptor included), so
     * those see the request Retrofit would have handed them.
     *
     * Never close the returned client while [okHttpClient] is still used elsewhere: closing it
     * evicts the shared connection pool and shuts down the shared dispatcher's executor.
     */
    fun create(okHttpClient: OkHttpClient): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        followRedirects = false
        useDefaultTransformers = false
        engine {
            preconfigured = okHttpClient
            config {
                followRedirects(okHttpClient.followRedirects)
                followSslRedirects(okHttpClient.followSslRedirects)
                retryOnConnectionFailure(okHttpClient.retryOnConnectionFailure)
                interceptors().add(0, TransportParityInterceptor)
            }
        }
    }

    /**
     * Runs before every other interceptor: undoes what only Ktor puts on a request, then reports
     * the URL the response finally came from (after OkHttp's redirects) back to [KtorPlanetApi].
     */
    private object TransportParityInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val exactUrl = request.header(KtorPlanetApi.EXACT_URL_HEADER)
            val sent = if (exactUrl != null || request.header("User-Agent") == KTOR_USER_AGENT) {
                request.newBuilder().apply {
                    if (exactUrl != null) {
                        url(Base64.decode(exactUrl).decodeToString())
                        removeHeader(KtorPlanetApi.EXACT_URL_HEADER)
                    }
                    if (request.header("User-Agent") == KTOR_USER_AGENT) removeHeader("User-Agent")
                }.build()
            } else {
                request
            }
            val response = chain.proceed(sent)
            // A fragment keeps its non-ASCII characters in an OkHttp URL; the header never leaves the process.
            val headers = response.headers.newBuilder()
                .removeAll(KtorPlanetApi.REQUEST_URL_HEADER)
                .addUnsafeNonAscii(KtorPlanetApi.REQUEST_URL_HEADER, response.request.url.toString())
                .build()
            return response.newBuilder().headers(headers).build()
        }
    }
}
