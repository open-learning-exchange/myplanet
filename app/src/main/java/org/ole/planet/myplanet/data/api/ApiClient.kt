package org.ole.planet.myplanet.data.api

import org.ole.planet.myplanet.data.NetworkResult
import org.ole.planet.myplanet.utils.RetryUtils
import retrofit2.Response

object ApiClient {
    private const val MAX_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 2000L

    suspend fun <T> executeWithRetryAndWrap(operation: suspend () -> Response<T>?): Response<T>? {
        return RetryUtils.retry(
            maxAttempts = MAX_ATTEMPTS,
            delayMs = RETRY_DELAY_MS,
            shouldRetry = { resp -> resp == null || !resp.isSuccessful },
            block = { operation() },
        )
    }

    suspend fun <T> executeWithResult(operation: suspend () -> Response<T>?): NetworkResult<T> {
        var lastException: Exception? = null
        val response = executeWithRetryAndWrap {
            try {
                operation()
            } catch (e: Exception) {
                lastException = e
                null
            }
        }

        return when {
            response == null -> NetworkResult.Exception(lastException ?: Exception("Unknown error"))
            response.isSuccessful -> {
                val body = response.body()
                if (body != null) {
                    NetworkResult.Success(body)
                } else {
                    NetworkResult.Error(response.code(), null)
                }
            }
            else -> {
                val errorBody = try { response.errorBody()?.string() } catch (_: Exception) { null }
                NetworkResult.Error(response.code(), errorBody)
            }
        }
    }
}
