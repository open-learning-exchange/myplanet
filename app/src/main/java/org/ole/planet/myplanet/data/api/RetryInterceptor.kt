package org.ole.planet.myplanet.data.api

import android.content.Intent
import java.io.IOException
import javax.inject.Inject
import kotlin.math.pow
import okhttp3.Interceptor
import okhttp3.Response
import org.ole.planet.myplanet.services.BroadcastService
import org.ole.planet.myplanet.utils.Constants

class RetryInterceptor @Inject constructor(
    private val broadcastService: BroadcastService
) : Interceptor {

    private val maxRetries = 3
    var initialDelay = 1000L
    private val factor = 2.0

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var tryCount = 0
        var response: Response? = null
        var lastError: IOException? = null

        while (true) {
            response?.close()
            response = null

            try {
                response = chain.proceed(request)
                lastError = null
                // Success (any non-5xx) is returned immediately.
                if (response.code !in 500..599) {
                    return response
                }
            } catch (e: IOException) {
                // Network failures (e.g. SocketTimeoutException) are retried like 5xx.
                lastError = e
            }

            if (tryCount >= maxRetries) {
                break
            }
            tryCount++

            val delay = (initialDelay * factor.pow(tryCount - 1)).toLong()

            val intent = Intent(Constants.ACTION_RETRY_EVENT).apply {
                putExtra("url", request.url.toString())
                putExtra("attempt", tryCount)
                putExtra("delay", delay)
            }

            broadcastService.trySendBroadcast(intent)

            try {
                Thread.sleep(delay)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted during retry delay", e)
            }
        }

        // Retries exhausted: return the last 5xx response, or rethrow the last network error.
        return response ?: throw (lastError ?: IOException("Request failed without a response"))
    }
}
