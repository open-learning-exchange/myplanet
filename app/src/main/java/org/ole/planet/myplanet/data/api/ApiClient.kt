package org.ole.planet.myplanet.data.api

import org.ole.planet.myplanet.data.NetworkResult
import org.ole.planet.myplanet.utils.RetryUtils

object ApiClient {
    private const val MAX_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 2000L

    private fun isRetryable(resp: ApiResponse<*>?): Boolean {
        return resp == null || resp.code in 500..599
    }

    suspend fun <T> executeWithRetryAndWrap(operation: suspend () -> ApiResponse<T>?): ApiResponse<T>? {
        return RetryUtils.retry(
            maxAttempts = MAX_ATTEMPTS,
            delayMs = RETRY_DELAY_MS,
            shouldRetry = { resp -> isRetryable(resp) },
            block = { operation() },
        )
    }

    suspend fun <T> executeWithResult(operation: suspend () -> ApiResponse<T>?): NetworkResult<T> {
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
                val body = response.body
                if (body != null) {
                    NetworkResult.Success(body)
                } else {
                    NetworkResult.Error(response.code, null)
                }
            }
            else -> {
                val errorBody = try { response.errorBody() } catch (_: Exception) { null }
                NetworkResult.Error(response.code, errorBody)
            }
        }
    }
}
