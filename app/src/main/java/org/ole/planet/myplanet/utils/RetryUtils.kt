package org.ole.planet.myplanet.utils

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
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

        while (attempt < maxAttempts) {
            try {
                result = block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                result = null
            }
            if (!shouldRetry(result)) {
                return result
            }
            attempt++
            if (attempt < maxAttempts) {
                delay(delayMs.milliseconds)
            }
        }
        return result
    }
}
