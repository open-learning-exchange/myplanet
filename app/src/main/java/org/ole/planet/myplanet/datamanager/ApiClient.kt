package org.ole.planet.myplanet.datamanager

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.delay
import org.ole.planet.myplanet.utilities.RetryUtils
import retrofit2.Response
import retrofit2.Retrofit

object ApiClient {
    lateinit var client: Retrofit

    suspend fun <T> executeWithRetryAndWrap(operation: suspend () -> Response<T>?): Response<T>? {
        return RetryUtils.retry(
            maxAttempts = 3,
            delayMs = 2000L,
            shouldRetry = { resp -> resp == null || !resp.isSuccessful },
            block = { operation() },
        )
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
