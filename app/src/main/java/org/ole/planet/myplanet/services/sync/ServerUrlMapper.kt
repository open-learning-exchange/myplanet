package org.ole.planet.myplanet.services.sync

import android.content.SharedPreferences
import android.net.Uri
import androidx.core.net.toUri
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.BuildConfig
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils

@Singleton
class ServerUrlMapper @Inject constructor(
    private val dispatcherProvider: DispatcherProvider
) {
    private val serverMappings = mapOf(
        "http://${BuildConfig.PLANET_SANPABLO_URL}" to "https://${BuildConfig.PLANET_SANPABLO_CLONE_URL}",
        "http://${BuildConfig.PLANET_URIUR_URL}" to "https://${BuildConfig.PLANET_URIUR_CLONE_URL}",
        "http://${BuildConfig.PLANET_EMBAKASI_URL}" to "https://${BuildConfig.PLANET_EMBAKASI_CLONE_URL}"
    )

    data class UrlMapping(
        val primaryUrl: String,
        val alternativeUrl: String? = null,
        val extractedBaseUrl: String? = null
    )

    private fun extractBaseUrl(url: String): String? {
        return try {
            val uri = url.toUri()
            val scheme = uri.scheme ?: return null
            val host = uri.host ?: return null
            val port = uri.port
            val isDefaultPort = (scheme == "http" && port == 80) || (scheme == "https" && port == 443)
            if (port != -1 && !isDefaultPort) "$scheme://$host:$port" else "$scheme://$host"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun processUrl(url: String): UrlMapping {
        val extractedUrl = extractBaseUrl(url)
        val alternativeUrl = extractedUrl?.let { baseUrl ->
            serverMappings[baseUrl].also {
            }
        }
        val result = UrlMapping(url, alternativeUrl, extractedUrl)
        return result
    }

    fun updateUrlPreferences(editor: SharedPreferences.Editor, uri: Uri, alternativeUrl: String, url: String, settings: SharedPreferences) {
        val altUri = alternativeUrl.toUri()
        val urlUser: String
        val urlPwd: String
        val altUserInfo = altUri.userInfo

        if (altUserInfo != null) {
            val (user, pwd) = UrlUtils.getUserInfo(altUserInfo)
            urlUser = user
            urlPwd = pwd
        } else {
            urlUser = "satellite"
            urlPwd = settings.getString("serverPin", "") ?: ""
        }

        val scheme = altUri.scheme
        val host = altUri.host
        val port = if (altUri.port == -1) {
            if (scheme == "http") 80 else 443
        } else {
            altUri.port
        }

        val couchdbURL = if (altUserInfo != null) {
            alternativeUrl
        } else {
            "$scheme://$urlUser:$urlPwd@$host:$port"
        }

        editor.apply {
            putString("url_user", urlUser)
            putString("url_pwd", urlPwd)
            putString("url_Scheme", uri.scheme)
            putString("url_Host", uri.host)
            putString("alternativeUrl", url)
            putString("processedAlternativeUrl", couchdbURL)
            putBoolean("isAlternativeUrl", true)
            apply()
        }
        UrlUtils.invalidateCaches()
    }

    suspend fun updateServerIfNecessary(
        mapping: UrlMapping, settings: SharedPreferences,
        isServerReachable: suspend (String) -> Boolean
    ) {
        val primaryAvailable = isServerReachable(mapping.primaryUrl)
        val alternativeAvailable = mapping.alternativeUrl?.let { altUrl ->
            isServerReachable(altUrl)
        } == true

        if (!primaryAvailable && alternativeAvailable) {
            mapping.alternativeUrl.let { alternativeUrl ->
                val editor = settings.edit()
                updateUrlPreferences(editor, mapping.primaryUrl.toUri(), alternativeUrl, mapping.primaryUrl, settings)
            }
        }
    }

    suspend fun isUrlDirectlyReachable(url: String): Boolean {
        return try {
            withContext(dispatcherProvider.io) {
                val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "http://$url" else url
                val connection = URL(cleanUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "GET"
                val code = connection.responseCode
                connection.disconnect()
                code in 200..599
            }
        } catch (e: Exception) {
            false
        }
    }
}
