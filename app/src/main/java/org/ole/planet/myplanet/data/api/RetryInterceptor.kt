package org.ole.planet.myplanet.data.api

import android.content.Intent
import java.io.IOException
import javax.inject.Inject
import kotlin.math.pow
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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
        var response = chain.proceed(request)
        var tryCount = 0

        while (response.code in 500..599 && tryCount < maxRetries) {
            tryCount++
            response.close()

            var delay = (initialDelay * factor.pow(tryCount - 1)).toLong()

            // Limit to a maximum sleep of 500ms
            if (delay > 500L) {
                delay = 500L
            }

            val intent = Intent(Constants.ACTION_RETRY_EVENT).apply {
                putExtra("url", request.url.toString())
                putExtra("attempt", tryCount)
                putExtra("delay", delay)
            }

            broadcastService.trySendBroadcast(intent)

            try {
                // Blocking is acceptable here as OkHttp interceptors run on background worker threads
                runBlocking {
                    delay(delay)
                }
            } catch (e: InterruptedException) {
                throw IOException("Interrupted during retry delay", e)
            }

            response = chain.proceed(request)
        }

        return response
    }
}
