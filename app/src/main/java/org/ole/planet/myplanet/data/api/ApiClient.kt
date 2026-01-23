package org.ole.planet.myplanet.data.api

import java.io.IOException
import java.net.SocketTimeoutException
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.data.NetworkResult
import org.ole.planet.myplanet.utils.RetryUtils
import retrofit2.Response
import retrofit2.Retrofit

object ApiClient {
    lateinit var client: Retrofit

    suspend fun ensureInitialized() {
        MainApplication.apiClientInitialized.await()
    }

    suspend fun <T> executeWithRetryAndWrap(operation: suspend () -> Response<T>?): Response<T>? {
        val startTime = System.currentTimeMillis()
        val result = RetryUtils.retry(
            maxAttempts = 3,
            initialDelay = 2000L,
            shouldRetry = { resp, _ -> resp == null || !resp.isSuccessful },
            block = { operation() },
        )
        val duration = System.currentTimeMillis() - startTime

        // Log the API call with timing
        try {
            val endpoint = extractEndpointFromStackTrace()
            val itemCount = if (result?.isSuccessful == true) {
                // Try to estimate item count from response body if it's a list/array
                when (val body = result.body()) {
                    is List<*> -> body.size
                    is com.google.gson.JsonObject -> {
                        if (body.has("rows")) {
                            body.getAsJsonArray("rows")?.size() ?: 0
                        } else 0
                    }
                    else -> 0
                }
            } else 0

            org.ole.planet.myplanet.utils.SyncTimeLogger.logApiCall(
                endpoint,
                duration,
                result?.isSuccessful == true,
                itemCount
            )
        } catch (e: Exception) {
            // Don't let logging errors affect the API call
            e.printStackTrace()
        }

        return result
    }

    private fun extractEndpointFromStackTrace(): String {
        // Try to extract endpoint from stack trace for logging
        return try {
            val stackTrace = Thread.currentThread().stackTrace
            stackTrace.find { it.className.contains("SyncManager") || it.className.contains("TransactionSyncManager") }
                ?.let { "${it.className.substringAfterLast(".")}.${it.methodName}" }
                ?: "unknown_endpoint"
        } catch (e: Exception) {
            "unknown_endpoint"
        }
    }

    suspend fun <T> executeWithResult(operation: suspend () -> Response<T>?): NetworkResult<T> {
        val result = RetryUtils.retry(
            maxAttempts = 3,
            initialDelay = 2000L,
            shouldRetry = { res, _ ->
                when (res) {
                    is NetworkResult.Error -> {
                        val code = res.code ?: 0
                        // Retry on timeout (408) or server errors (5xx)
                        code == 408 || code == 503 || (code in 500..599)
                    }
                    is NetworkResult.Exception -> {
                        val ex = res.exception
                        ex is IOException || ex is SocketTimeoutException
                    }
                    else -> false
                }
            }
        ) {
            try {
                val response = operation()
                if (response != null) {
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null) {
                            NetworkResult.Success(body)
                        } else {
                            NetworkResult.Error(response.code(), "Empty body")
                        }
                    } else {
                        val errorBody = try { response.errorBody()?.string() } catch (_: Exception) { null }
                        NetworkResult.Error(response.code(), errorBody)
                    }
                } else {
                    NetworkResult.Exception(IOException("Response is null"))
                }
            } catch (e: Exception) {
                NetworkResult.Exception(e)
            }
        }
        return result ?: NetworkResult.Exception(Exception("Unknown error"))
    }
}
