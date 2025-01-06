package org.ole.planet.myplanet.utilities

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import org.ole.planet.myplanet.BuildConfig
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME

class ServerUrlMapper(
    private val context: Context,
    private val settings: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    private val editor: SharedPreferences.Editor = settings.edit()
) {
    private val serverMappings = mapOf(
        "http://${BuildConfig.PLANET_URIUR_URL}" to "https://${BuildConfig.PLANET_URIUR_CLONE_URL}",
        "http://${BuildConfig.PLANET_EMBAKASI_URL}" to "https://${BuildConfig.PLANET_EMBAKASI_CLONE_URL}"
    )

    val validUrls = listOf(
        "https://${BuildConfig.PLANET_GUATEMALA_URL}",
        "http://${BuildConfig.PLANET_XELA_URL}",
        "http://${BuildConfig.PLANET_URIUR_URL}",
        "http://${BuildConfig.PLANET_SANPABLO_URL}",
        "http://${BuildConfig.PLANET_EMBAKASI_URL}",
        "https://${BuildConfig.PLANET_VI_URL}"
    )

    data class UrlMapping(
        val primaryUrl: String,
        val alternativeUrl: String? = null,
        val extractedBaseUrl: String? = null
    )

    private fun extractBaseUrl(url: String): String? {
        val regex = Regex("^(https?://).*?@(.*?):")
        return regex.find(url)?.let {
            val protocol = it.groupValues[1]
            val address = it.groupValues[2]
            "$protocol$address"
        }
    }

    fun processUrl(url: String): UrlMapping {
        val extractedUrl = extractBaseUrl(url)
        Log.d("URLMapping", "Original URL being processed: $url")
        Log.d("URLMapping", "Extracted base URL: $extractedUrl")

        val primaryUrlMapping = serverMappings.entries.find { it.key.contains(extractedUrl ?: "") }

        return if (primaryUrlMapping != null) {
            val primaryUrl = primaryUrlMapping.key
            val alternativeUrl = primaryUrlMapping.value

            Log.d("URLMapping", "Mapped Primary URL: $primaryUrl")
            Log.d("URLMapping", "Mapped Alternative URL: $alternativeUrl")

            UrlMapping(url, alternativeUrl, extractedUrl)
        } else {
            Log.w("URLMapping", "No URL mapping found for: $extractedUrl")
            UrlMapping(url, null, extractedUrl)
        }
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

        // Try primary URL first
        Log.d(TAG, "Attempting to reach primary URL: $url")
        val isPrimaryReachable = isServerReachable(url)

        if (isPrimaryReachable) {
            Log.d(TAG, "Successfully reached primary URL: $url")
            onStartSync()
            onLogSync()
            return ConnectionResult.Success(url)
        }

        // If primary fails and we have an alternative, try that
        if (mapping.alternativeUrl != null) {
            Log.w(TAG, "Failed to reach primary URL: $url")
            val uri = Uri.parse(mapping.alternativeUrl)
            updateUrlPreferences(editor, uri, mapping.alternativeUrl, url, settings)

            val processedUrl = settings.getString("processedAlternativeUrl", "")
            if (!processedUrl.isNullOrEmpty()) {
                Log.d(TAG, "Attempting to reach alternative URL: $processedUrl")
                val isAlternativeReachable = isServerReachable(processedUrl)

                if (isAlternativeReachable) {
                    onStartSync()
                    onLogSync()
                    return ConnectionResult.Success(processedUrl)
                }
            }

            return ConnectionResult.Failure(url, mapping.alternativeUrl)
        }

        // If no alternative URL exists, try original URL one last time
        Log.d(TAG, "No alternative URL available, proceeding with original URL")
        onStartSync()
        onLogSync()
        return ConnectionResult.Success(url)
    }

    fun createAuthenticatedUrl(baseUrl: String): String {
        val uri = Uri.parse(baseUrl)
        return if (baseUrl.contains("@")) {
            baseUrl
        } else {
            val urlUser = "satellite"
            val urlPwd = settings.getString("serverPin", "") ?: ""
            "${uri.scheme}://$urlUser:$urlPwd@${uri.host}:${if (uri.port == -1) (if (uri.scheme == "http") 80 else 443) else uri.port}"
        }
    }

    companion object {
        private const val TAG = "ServerUrlMapper"
    }
}
