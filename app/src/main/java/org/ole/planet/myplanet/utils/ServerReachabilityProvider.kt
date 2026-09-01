package org.ole.planet.myplanet.utils

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.ole.planet.myplanet.di.StandardHttpClient
import org.ole.planet.myplanet.services.sync.ServerUrlMapper

@Singleton
class ServerReachabilityProvider @Inject constructor(
    @StandardHttpClient private val okHttpClient: OkHttpClient,
    private val serverUrlMapper: ServerUrlMapper,
    private val dispatcherProvider: DispatcherProvider,
    private val timeProvider: TimeProvider
) {
    private val reachabilityCache = ConcurrentHashMap<String, Pair<Boolean, Long>>()
    private val REACHABILITY_CACHE_TTL_MS = 30_000L

    suspend fun isServerReachable(urlString: String): Boolean {
        if (urlString.isBlank()) return false

        reachabilityCache[urlString]?.let { (reachable, checkedAt) ->
            if (timeProvider.now() - checkedAt < REACHABILITY_CACHE_TTL_MS) {
                return reachable
            }
        }

        val mapping = serverUrlMapper.processUrl(urlString)
        val urlsToTry = mutableListOf(urlString)
        mapping.alternativeUrl?.let { urlsToTry.add(it) }

        var reachable = false
        for (url in urlsToTry) {
            if (tryConnect(url)) {
                reachable = true
                break
            }
        }
        reachabilityCache[urlString] = reachable to timeProvider.now()
        return reachable
    }

    suspend fun isPrimaryServerReachable(urlString: String): Boolean {
        if (urlString.isBlank()) return false
        return tryConnect(urlString)
    }

    private suspend fun tryConnect(urlString: String): Boolean = withContext(dispatcherProvider.io) {
        try {
            val formattedUrl = if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                "http://$urlString"
            } else {
                urlString
            }
            val request = Request.Builder().url(formattedUrl).head().build()
            okHttpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            false
        }
    }
}
