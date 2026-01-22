package org.ole.planet.myplanet.utils

import kotlinx.coroutines.delay
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

object RetryUtils {
    /**
     * Retries a suspend block with exponential backoff and jitter.
     *
     * @param maxAttempts Maximum number of retry attempts.
     * @param initialDelay Initial delay in milliseconds.
     * @param maxDelay Maximum delay in milliseconds.
     * @param multiplier Multiplier for exponential backoff.
     * @param jitter Jitter factor in milliseconds.
     * @param shouldRetry Predicate to determine if a retry should be attempted based on result or exception.
     * @param block The suspend block to execute.
     */
    suspend fun <T> retry(
        maxAttempts: Int = 3,
        initialDelay: Long = 1000L,
        maxDelay: Long = 5000L,
        multiplier: Double = 2.0,
        jitter: Long = 500L,
        shouldRetry: (T?, Exception?) -> Boolean = { result, exception ->
            exception != null || result == null
        },
        block: suspend () -> T?
    ): T? {
        var attempt = 0
        var result: T? = null
        var lastException: Exception? = null

        while (attempt < maxAttempts) {
            try {
                result = block()
                lastException = null
            } catch (e: Exception) {
                lastException = e
                result = null
            }

            if (!shouldRetry(result, lastException)) {
                return result
            }

            attempt++
            if (attempt < maxAttempts) {
                val backoff = (initialDelay * multiplier.pow(attempt.toDouble())).toLong()
                val actualDelay = min(backoff, maxDelay)
                val delayTime = actualDelay + if (jitter > 0) Random.nextLong(0, jitter) else 0
                delay(delayTime)
            }
        }
        lastException?.printStackTrace()
        return result
    }

    fun isRetriableError(e: Throwable): Boolean {
        return when (e) {
            is SocketTimeoutException -> true
            is IOException -> true
            else -> false
        }
    }
}
