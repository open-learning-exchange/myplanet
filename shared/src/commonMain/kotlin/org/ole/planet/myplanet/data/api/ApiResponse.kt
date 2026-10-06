package org.ole.planet.myplanet.data.api

/**
 * Platform-free view of an HTTP response: exactly the parts callers read, nothing more.
 *
 * [body] and [errorBody] are produced on first access, mirroring the transport they were
 * read from — a body nobody reads is never decoded, and an error body is only turned into
 * text when a caller asks for it.
 */
class ApiResponse<out T>(
    val code: Int,
    val message: String,
    private val headers: List<Pair<String, String>> = emptyList(),
    /** The URL of the request that produced this response, after redirects. */
    val requestUrl: String? = null,
    bodyReader: () -> T? = { null },
    private val errorBodyReader: () -> String? = { null },
) {
    val isSuccessful: Boolean get() = code in 200..299

    val body: T? by lazy(bodyReader)

    fun errorBody(): String? = errorBodyReader()

    /** Last value of the header [name], compared case-insensitively; null when absent. */
    fun header(name: String): String? = headers.lastOrNull { it.first.equals(name, ignoreCase = true) }?.second

    companion object {
        fun <T> success(
            body: T?,
            code: Int = 200,
            headers: List<Pair<String, String>> = emptyList(),
            requestUrl: String? = null,
        ): ApiResponse<T> = ApiResponse(code, "OK", headers, requestUrl, bodyReader = { body })

        fun <T> error(code: Int, errorBody: String? = null, requestUrl: String? = null): ApiResponse<T> =
            ApiResponse(code, "Response.error()", requestUrl = requestUrl, errorBodyReader = { errorBody })
    }
}
