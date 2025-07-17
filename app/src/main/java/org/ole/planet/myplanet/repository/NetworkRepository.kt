package org.ole.planet.myplanet.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class NetworkRepository {
    suspend fun isServerReachable(urlString: String): Boolean {
        if (urlString.isBlank()) return false
        val formattedUrl = if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
            "http://$urlString"
        } else {
            urlString
        }
        return try {
            val url = URL(formattedUrl)
            val connection = withContext(Dispatchers.IO) { url.openConnection() } as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            withContext(Dispatchers.IO) { connection.connect() }
            val responseCode = connection.responseCode
            connection.disconnect()
            responseCode in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
