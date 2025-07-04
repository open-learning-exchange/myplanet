package org.ole.planet.myplanet.utilities

import android.content.SharedPreferences
import android.net.Uri
import androidx.core.net.toUri
import org.ole.planet.myplanet.BuildConfig

class ServerUrlMapper() {
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
            val baseUrl = "${uri.scheme}://${uri.host}${if (uri.port != -1) ":${uri.port}" else ""}"
            baseUrl
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun processUrl(url: String): UrlMapping {
        val extractedUrl = extractBaseUrl(url)
        val alternativeUrl = extractedUrl?.let { baseUrl ->
            val mappedUrl = serverMappings[baseUrl]
            mappedUrl
        }

        val result = UrlMapping(url, alternativeUrl, extractedUrl)
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

        val couchdbURL = if (alternativeUrl.contains("@")) {
            alternativeUrl
        } else {
            "${altUri.scheme}://$urlUser:$urlPwd@${altUri.host}:${if (altUri.port == -1) (if (altUri.scheme == "http") 80 else 443) else altUri.port}"
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
        val primaryAvailable = isServerReachable(mapping.primaryUrl)
        val alternativeAvailable = mapping.alternativeUrl?.let { isServerReachable(it) } == true

        if (!primaryAvailable && alternativeAvailable) {
            mapping.alternativeUrl.let { alternativeUrl ->
                val editor = settings.edit()
                updateUrlPreferences(editor, mapping.primaryUrl.toUri(), alternativeUrl, mapping.primaryUrl, settings)
            }
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
