package org.ole.planet.myplanet.data.api

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.delay
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
            initialDelay = 1000L,
            maxDelay = 5000L,
            multiplier = 2.0,
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
        var lastException: Exception? = null

        val response = RetryUtils.retry(
            maxAttempts = 3,
            initialDelay = 1000L,
            maxDelay = 5000L,
            multiplier = 2.0,
            shouldRetry = { resp, ex ->
                if (ex != null) {
                    lastException = ex
                    return@retry RetryUtils.isRetriableError(ex)
                }
                if (resp != null) {
                    if (resp.isSuccessful) return@retry false
                    val code = resp.code()
                    // Retry on 5xx server errors or 408 Timeout
                    // 401, 403, 404 are permanent errors usually
                    return@retry code in 500..599 || code == 408
                }
                return@retry true // resp is null, retry
            }
        ) {
            operation()
        }

        if (response != null && response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                return NetworkResult.Success(body)
            }
            return NetworkResult.Error(response.code(), "Empty body")
        }

        if (response != null) {
            val errorBody = try { response.errorBody()?.string() } catch (_: Exception) { null }
            return NetworkResult.Error(response.code(), errorBody)
        }

        return NetworkResult.Exception(lastException ?: Exception("Unknown error"))
    }
}
