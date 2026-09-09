package org.ole.planet.myplanet.services

import android.net.TrafficStats
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.di.CoreDependenciesEntryPoint
import org.ole.planet.myplanet.utils.Constants.NETWORK_TRAFFIC_TAG

class ServerReachabilityChecker(
    private val coreDependenciesEntryPoint: () -> CoreDependenciesEntryPoint
) {
    private val reachabilityCache = ConcurrentHashMap<String, Pair<Boolean, Long>>()

    suspend fun isServerReachable(urlString: String, ioDispatcher: CoroutineDispatcher): Boolean {
        if (urlString.isBlank()) return false

        reachabilityCache[urlString]?.let { (reachable, checkedAt) ->
            if (System.currentTimeMillis() - checkedAt < REACHABILITY_CACHE_TTL_MS) {
                return reachable
            }
        }

        val serverUrlMapper = coreDependenciesEntryPoint().serverUrlMapper()
        val mapping = serverUrlMapper.processUrl(urlString)
        val urlsToTry = mutableListOf(urlString)
        mapping.alternativeUrl?.let { urlsToTry.add(it) }

        var reachable = false
        for (url in urlsToTry) {
            if (tryConnect(url, ioDispatcher)) {
                reachable = true
                break
            }
        }
        reachabilityCache[urlString] = reachable to System.currentTimeMillis()
        return reachable
    }

    suspend fun isPrimaryServerReachable(urlString: String, ioDispatcher: CoroutineDispatcher): Boolean {
        if (urlString.isBlank()) return false
        return tryConnect(urlString, ioDispatcher)
    }

    private suspend fun tryConnect(urlString: String, ioDispatcher: CoroutineDispatcher): Boolean {
        return try {
            val formattedUrl = if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                "http://$urlString"
            } else {
                urlString
            }
            val url = URL(formattedUrl)
            val responseCode = withContext(ioDispatcher) {
                getResponseCode(url)
            }
            responseCode in 200..299
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            false
        }
    }

    private fun getResponseCode(url: URL): Int {
        TrafficStats.setThreadStatsTag(NETWORK_TRAFFIC_TAG)
        return try {
            val headCode = executeRequest(url, "HEAD")
            if (headCode == HttpURLConnection.HTTP_BAD_METHOD || headCode == HttpURLConnection.HTTP_NOT_IMPLEMENTED) {
                executeRequest(url, "GET")
            } else {
                headCode
            }
        } finally {
            TrafficStats.clearThreadStatsTag()
        }
    }

    private fun executeRequest(url: URL, method: String): Int {
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val REACHABILITY_CACHE_TTL_MS = 30_000L
    }
}
