package org.ole.planet.myplanet.utilities

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object RetryUtils {
    suspend fun <T> retry(
        scope: CoroutineScope,
        maxAttempts: Int = 3,
        delayMs: Long = 2000L,
        shouldRetry: (T?) -> Boolean = { it == null },
        block: suspend () -> T?,
    ): T? = withContext(scope.coroutineContext) {
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
                return@withContext result
            }
            attempt++
            if (attempt < maxAttempts) {
                delay(delayMs)
            }
        }
        lastException?.printStackTrace()
        result
    }
}
