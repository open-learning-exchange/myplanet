package org.ole.planet.myplanet.utils

import kotlinx.coroutines.delay

object RetryUtils {
    suspend fun <T> retry(
        maxAttempts: Int = 3,
        initialDelay: Long = 2000L,
        maxDelay: Long = 10000L,
        multiplier: Double = 2.0,
        jitter: Long = 0L,
        shouldRetry: (T?, Exception?) -> Boolean = { result, _ -> result == null },
        delayHook: suspend (Long) -> Unit = { delay(it) },
        block: suspend () -> T?
    ): T? {
        var attempt = 0
        var result: T? = null
        var lastException: Exception? = null
        var currentDelay = initialDelay

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
                val jitterDelay = if (jitter > 0) (Math.random() * jitter).toLong() else 0
                delayHook(currentDelay + jitterDelay)
                currentDelay = (currentDelay * multiplier).toLong().coerceAtMost(maxDelay)
            }
        }
        lastException?.printStackTrace()
        return result
    }
}
