package org.ole.planet.myplanet.services.sync

import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import javax.inject.Inject
import javax.inject.Singleton
import org.ole.planet.myplanet.BuildConfig
import org.ole.planet.myplanet.MainApplication

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
            val normalized = if (url.contains("://")) url else "http://$url"
            val uri = normalized.toUri()
            "${uri.scheme}://${uri.host}"
        } catch (e: Exception) {
            Log.e(MainApplication.TAG, "extractBaseUrl failed for '$url': ${e.message}")
            null
        }
    }

    fun processUrl(url: String): UrlMapping {
        val extractedUrl = extractBaseUrl(url)
        val alternativeUrl = extractedUrl?.let { baseUrl ->
            val mappedUrl = serverMappings[baseUrl]
            mappedUrl
        }
        Log.d(MainApplication.TAG, "processUrl: input='$url' extractedBase='$extractedUrl' alternative='$alternativeUrl'")
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
        Log.d(MainApplication.TAG, "updateServerIfNecessary: checking primary='${mapping.primaryUrl}' alternative='${mapping.alternativeUrl}'")
        val primaryAvailable = isServerReachable(mapping.primaryUrl)
        Log.d(MainApplication.TAG, "updateServerIfNecessary: primaryAvailable=$primaryAvailable")
        val alternativeAvailable = mapping.alternativeUrl?.let { isServerReachable(it) } == true
        Log.d(MainApplication.TAG, "updateServerIfNecessary: alternativeAvailable=$alternativeAvailable")

        if (!primaryAvailable && alternativeAvailable) {
            Log.d(MainApplication.TAG, "updateServerIfNecessary: switching to alternative '${mapping.alternativeUrl}'")
            mapping.alternativeUrl.let { alternativeUrl ->
                val editor = settings.edit()
                updateUrlPreferences(editor, mapping.primaryUrl.toUri(), alternativeUrl, mapping.primaryUrl, settings)
            }
        } else {
            Log.d(MainApplication.TAG, "updateServerIfNecessary: no switch (primary=$primaryAvailable, alternative=$alternativeAvailable)")
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
