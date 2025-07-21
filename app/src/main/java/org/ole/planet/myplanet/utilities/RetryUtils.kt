package org.ole.planet.myplanet.utilities

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

object RetryUtils {
    suspend fun <T> retry(
        maxAttempts: Int = 3,
        delayMs: Long = 2000L,
        shouldRetry: (T?) -> Boolean = { it == null },
        block: suspend () -> T?
    ): T? {
        var attempt = 0
        var result: T? = null
        var lastException: Exception? = null

        while (attempt < maxAttempts) {
            try {
                result = block()
            } catch (e: Exception) {
                lastException = e
                result = null
            }
            if (!shouldRetry(result)) {
                return result
            }
            attempt++
            if (attempt < maxAttempts) {
                try {
                    delay(delayMs)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Exception) {
                    // ignore other exceptions
                }
            }
        }
        lastException?.printStackTrace()
        return result
    }
}

