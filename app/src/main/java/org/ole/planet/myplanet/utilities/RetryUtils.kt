package org.ole.planet.myplanet.utilities

object RetryUtils {
    fun <T> retry(
        maxAttempts: Int = 3,
        delayMs: Long = 2000L,
        shouldRetry: (T?) -> Boolean = { it == null },
        block: () -> T?
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
                    Thread.sleep(delayMs)
                } catch (ie: InterruptedException) {
                    // ignore
                }
            }
        }
        lastException?.printStackTrace()
        return result
    }
}
