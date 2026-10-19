package org.ole.planet.myplanet.utils

import java.net.HttpURLConnection
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.ole.planet.myplanet.di.ReachabilityHttpClient
import org.ole.planet.myplanet.services.sync.ServerUrlMapper

@Singleton
class ServerReachabilityProvider @Inject constructor(
    @ReachabilityHttpClient private val okHttpClient: OkHttpClient,
    private val serverUrlMapper: ServerUrlMapper,
    private val dispatcherProvider: DispatcherProvider,
    private val timeProvider: TimeProvider
) {
    private val reachabilityCache = ConcurrentHashMap<String, Pair<Boolean, Long>>()

    suspend fun isServerReachable(urlString: String): Boolean {
        if (urlString.isBlank()) return false

        reachabilityCache[urlString]?.let { (reachable, checkedAt) ->
            if (timeProvider.now() - checkedAt < REACHABILITY_CACHE_TTL_MS) {
                return reachable
            }
        }

        val mapping = serverUrlMapper.processUrl(urlString)
        val urlsToTry = listOfNotNull(urlString, mapping.alternativeUrl)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        if (urlsToTry.isEmpty()) return false

        val trackers = urlsToTry.map { ProbeTracker() }
        val channel = Channel<Boolean>(urlsToTry.size)

        var reachable = false
        try {
            coroutineScope {
                urlsToTry.forEachIndexed { index, url ->
                    val tracker = trackers[index]
                    launch {
                        val success = try {
                            tryConnect(url, tracker)
                        } finally {
                            tracker.markFinished()
                        }
                        channel.send(success)
                    }
                }

                var completedCount = 0
                while (completedCount < urlsToTry.size) {
                    val result = channel.receive()
                    completedCount++
                    if (result) {
                        reachable = true
                        trackers.forEach { it.cancelIfInFlight() }
                        coroutineContext.cancelChildren()
                        break
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return false
        }

        reachabilityCache[urlString] = reachable to timeProvider.now()
        return reachable
    }

    suspend fun isPrimaryServerReachable(urlString: String): Boolean {
        if (urlString.isBlank()) return false
        return tryConnect(urlString)
    }

    private suspend fun tryConnect(urlString: String, tracker: ProbeTracker? = null): Boolean =
        withContext(dispatcherProvider.io) {
            try {
                val formattedUrl = if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                    "http://$urlString"
                } else {
                    urlString
                }
                val (successful, code) = probe(formattedUrl, useHead = true, tracker = tracker)
                when {
                    successful -> true
                    // Servers that refuse HEAD are still reachable, so confirm with a GET.
                    code == HttpURLConnection.HTTP_BAD_METHOD || code == HttpURLConnection.HTTP_NOT_IMPLEMENTED -> {
                        ensureActive()
                        probe(formattedUrl, useHead = false, tracker = tracker).first
                    }
                    else -> false
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                false
            }
        }

    private fun probe(urlString: String, useHead: Boolean, tracker: ProbeTracker? = null): Pair<Boolean, Int> {
        val request = Request.Builder().url(urlString).apply {
            if (useHead) head() else get()
        }.build()
        val call = okHttpClient.newCall(request)
        tracker?.onCallStarted(call)
        return try {
            call.execute().use { response ->
                response.isSuccessful to response.code
            }
        } finally {
            tracker?.onCallFinished()
        }
    }

    private class ProbeTracker {
        @Volatile
        var isFinished: Boolean = false
            private set

        @Volatile
        var currentCall: Call? = null
            private set

        @Volatile
        private var isCancelled: Boolean = false

        fun onCallStarted(call: Call) {
            currentCall = call
            // A cancel that landed before this call existed must still stop it.
            if (isCancelled) call.cancel()
        }

        fun onCallFinished() {
            currentCall = null
        }

        fun markFinished() {
            isFinished = true
        }

        fun cancelIfInFlight() {
            if (isFinished) return
            isCancelled = true
            val call = currentCall
            if (call != null && !isFinished) {
                try {
                    call.cancel()
                } catch (_: Exception) {
                }
            }
        }
    }

    private companion object {
        const val REACHABILITY_CACHE_TTL_MS = 30_000L
    }
}
