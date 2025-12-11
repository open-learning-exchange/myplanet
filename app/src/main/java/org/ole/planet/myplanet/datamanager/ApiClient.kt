package org.ole.planet.myplanet.datamanager

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.delay
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.utilities.RetryUtils
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
            delayMs = 2000L,
            shouldRetry = { resp -> resp == null || !resp.isSuccessful },
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

            org.ole.planet.myplanet.utilities.SyncTimeLogger.logApiCall(
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
        var retryCount = 0
        var lastException: Exception? = null

        while (retryCount < 3) {
            try {
                val response = operation()
                if (response != null) {
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null) {
                            return NetworkResult.Success(body)
                        }
                        return NetworkResult.Error(response.code(), null)
                    } else if (retryCount < 2) {
                        retryCount++
                        delay(2000L * (retryCount + 1))
                        continue
                    } else {
                        val errorBody = try { response.errorBody()?.string() } catch (_: Exception) { null }
                        return NetworkResult.Error(response.code(), errorBody)
                    }
                }
            } catch (e: SocketTimeoutException) {
                lastException = e
            } catch (e: IOException) {
                lastException = e
            } catch (e: Exception) {
                lastException = e
            }

            if (retryCount < 2) {
                retryCount++
                delay(2000L * (retryCount + 1))
            } else {
                break
            }
        }
        return NetworkResult.Exception(lastException ?: Exception("Unknown error"))
    }
}
