package org.ole.planet.myplanet.data.api

import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.data.NetworkResult
import org.ole.planet.myplanet.utils.RetryUtils
import retrofit2.Response
import retrofit2.Retrofit

object ApiClient {
    lateinit var client: Retrofit

    private const val MAX_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 2000L

    suspend fun ensureInitialized() {
        MainApplication.apiClientInitialized.await()
    }

    /**
     * Runs [operation] with up to [MAX_ATTEMPTS] attempts, retrying on a null or
     * unsuccessful response (exceptions are treated as a null result by
     * [RetryUtils.retry]). Returns the last response, or null if every attempt failed.
     */
    suspend fun <T> executeWithRetryAndWrap(operation: suspend () -> Response<T>?): Response<T>? {
        return RetryUtils.retry(
            maxAttempts = MAX_ATTEMPTS,
            delayMs = RETRY_DELAY_MS,
            shouldRetry = { resp -> resp == null || !resp.isSuccessful },
            block = { operation() },
        )
    }

    /**
     * Same retry policy as [executeWithRetryAndWrap], mapping the outcome to a typed
     * [NetworkResult]: [NetworkResult.Success] for a 2xx with a body,
     * [NetworkResult.Error] for an HTTP error, and [NetworkResult.Exception] when every
     * attempt failed without producing a response.
     */
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
