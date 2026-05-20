package org.ole.planet.myplanet.services.sync

import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import javax.inject.Inject
import javax.inject.Singleton
import org.ole.planet.myplanet.BuildConfig

private const val TAG = "ServerUrlMapper"

@Singleton
class ServerUrlMapper @Inject constructor() {
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
            serverMappings[baseUrl].also { mapped ->
                if (mapped != null) {
                    Log.d(TAG, "processUrl: mapped $baseUrl → $mapped")
                } else {
                    Log.d(TAG, "processUrl: no mapping found for base '$baseUrl' (${serverMappings.size} mappings registered)")
                }
            }
        }
        val result = UrlMapping(url, alternativeUrl, extractedUrl)
        Log.d(TAG, "processUrl: result — primary=${result.primaryUrl.substringAfterLast('/')} extractedBase=$extractedUrl alternative=$alternativeUrl")
        return result
    }

    fun updateUrlPreferences(editor: SharedPreferences.Editor, uri: Uri, alternativeUrl: String, url: String, settings: SharedPreferences) {
        val urlUser: String
        val urlPwd: String

        if (alternativeUrl.contains("@")) {
            val userinfo = getUserInfo(uri)
            urlUser = userinfo[0]
            urlPwd = userinfo[1]
        } else {
            urlUser = "satellite"
            urlPwd = settings.getString("serverPin", "") ?: ""
        }

        val altUri = alternativeUrl.toUri()
        val scheme = altUri.scheme
        val host = altUri.host
        val port = if (altUri.port == -1) {
            if (scheme == "http") 80 else 443
        } else {
            altUri.port
        }

        val couchdbURL = if (alternativeUrl.contains("@")) {
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
    }

    suspend fun updateServerIfNecessary(
        mapping: UrlMapping,
        settings: SharedPreferences,
        isServerReachable: suspend (String) -> Boolean
    ) {
        Log.d(TAG, "updateServerIfNecessary: checking primary=${mapping.primaryUrl}")
        val primaryAvailable = isServerReachable(mapping.primaryUrl)
        Log.d(TAG, "updateServerIfNecessary: primary reachable=$primaryAvailable")

        val alternativeAvailable = mapping.alternativeUrl?.let { altUrl ->
            Log.d(TAG, "updateServerIfNecessary: checking alternative=$altUrl")
            isServerReachable(altUrl).also { Log.d(TAG, "updateServerIfNecessary: alternative reachable=$it") }
        } == true

        if (!primaryAvailable && alternativeAvailable) {
            Log.d(TAG, "updateServerIfNecessary: primary down, alternative up — switching to ${mapping.alternativeUrl}")
            mapping.alternativeUrl.let { alternativeUrl ->
                val editor = settings.edit()
                updateUrlPreferences(editor, mapping.primaryUrl.toUri(), alternativeUrl, mapping.primaryUrl, settings)
            }
        } else if (primaryAvailable) {
            Log.d(TAG, "updateServerIfNecessary: primary is reachable, no switch needed")
        } else {
            Log.w(TAG, "updateServerIfNecessary: both primary and alternative are unreachable")
        }
    }

    private fun getUserInfo(uri: Uri): Array<String> {
        val defaultInfo = arrayOf("", "")
        val info = uri.userInfo?.split(":")?.dropLastWhile { it.isEmpty() }?.toTypedArray()

        val result = if ((info?.size ?: 0) > 1) {
            arrayOf(info!![0], info[1])
        } else {
            defaultInfo
        }
        return result
    }
}
