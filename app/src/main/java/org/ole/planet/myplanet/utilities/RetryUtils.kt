package org.ole.planet.myplanet.utilities

import android.util.Log

object RetryUtils {
    private const val TAG = "BECOME_MEMBER"

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
            Log.d(TAG, "[RetryUtils] Attempt ${attempt + 1} of $maxAttempts")
            try {
                result = block()
                Log.d(TAG, "[RetryUtils] Attempt ${attempt + 1} completed, result: ${if (result == null) "null" else "not null"}")
            } catch (e: Exception) {
                Log.e(TAG, "[RetryUtils] Attempt ${attempt + 1} failed with exception: ${e.message}", e)
                lastException = e
                result = null
            }
            if (!shouldRetry(result)) {
                Log.d(TAG, "[RetryUtils] Success on attempt ${attempt + 1}, returning result")
                return result
            }
            Log.w(TAG, "[RetryUtils] Attempt ${attempt + 1} needs retry")
            attempt++
            if (attempt < maxAttempts) {
                Log.d(TAG, "[RetryUtils] Waiting ${delayMs}ms before next attempt")
                kotlinx.coroutines.delay(delayMs)
            }
        }
        if (lastException != null) {
            Log.e(TAG, "[RetryUtils] All $maxAttempts attempts failed, last exception: ${lastException.message}")
            lastException.printStackTrace()
        } else {
            Log.e(TAG, "[RetryUtils] All $maxAttempts attempts failed, no exception but shouldRetry returned true")
        }
        return result
    }
}
