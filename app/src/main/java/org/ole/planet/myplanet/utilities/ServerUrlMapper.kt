package org.ole.planet.myplanet.utilities

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.ole.planet.myplanet.BuildConfig
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME

class ServerUrlMapper(private val context: Context, private val settings: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE), private val editor: SharedPreferences.Editor = settings.edit()) {
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
            val uri = Uri.parse(url)
            "${uri.scheme}://${uri.host}${if (uri.port != -1) ":${uri.port}" else ""}"
        } catch (e: Exception) {
            null
        }
    }

    fun processUrl(url: String): UrlMapping {
        val extractedUrl = extractBaseUrl(url)
        val alternativeUrl = extractedUrl?.let { serverMappings[it] }
        return UrlMapping(url, alternativeUrl, extractedUrl)
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

        val couchdbURL = if (alternativeUrl.contains("@")) {
            alternativeUrl
        } else {
            "${uri.scheme}://$urlUser:$urlPwd@${uri.host}:${if (uri.port == -1) (if (uri.scheme == "http") 80 else 443) else uri.port}"
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

    private fun getUserInfo(uri: Uri): Array<String> {
        val defaultInfo = arrayOf("", "")
        val info = uri.userInfo?.split(":")?.dropLastWhile { it.isEmpty() }?.toTypedArray()

        return if ((info?.size ?: 0) > 1) {
            arrayOf(info!![0], info[1])
        } else {
            defaultInfo
        }
    }

    sealed class ConnectionResult {
        data class Success(val url: String) : ConnectionResult()
        data class Failure(val primaryUrl: String, val alternativeUrl: String? = null) : ConnectionResult()
    }

    suspend fun performUrlSync(url: String, onStartSync: () -> Unit, onLogSync: () -> Unit): ConnectionResult {
        val mapping = processUrl(url)

        val isPrimaryReachable = MainApplication.isServerReachable(url)

        if (isPrimaryReachable) {
            onStartSync()
            onLogSync()
            return ConnectionResult.Success(url)
        }

        if (mapping.alternativeUrl != null) {

            val uri = Uri.parse(mapping.alternativeUrl)
            updateUrlPreferences(editor, uri, mapping.alternativeUrl, url, settings)

            val processedUrl = settings.getString("processedAlternativeUrl", "")
            if (!processedUrl.isNullOrEmpty()) {
                val isAlternativeReachable = MainApplication.isServerReachable(processedUrl)

                if (isAlternativeReachable) {
                    onStartSync()
                    onLogSync()
                    return ConnectionResult.Success(processedUrl)
                }
            }

            return ConnectionResult.Failure(url, mapping.alternativeUrl)
        }

        onStartSync()
        onLogSync()
        return ConnectionResult.Success(url)
    }
}
