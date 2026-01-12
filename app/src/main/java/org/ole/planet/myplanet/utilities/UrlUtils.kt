package org.ole.planet.myplanet.utilities

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.core.net.toUri
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME

object UrlUtils {
    val header: String
        get() {
            val settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val credentials = "${settings.getString("url_user", "")}:${settings.getString("url_pwd", "")}".toByteArray()
            return "Basic ${Base64.encodeToString(credentials, Base64.NO_WRAP)}"
        }

    val hostUrl: String
        get() {
            val settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            var scheme = settings.getString("url_Scheme", "")
            var hostIp = settings.getString("url_Host", "")
            val isAlternativeUrl = settings.getBoolean("isAlternativeUrl", false)
            val alternativeUrl = settings.getString("processedAlternativeUrl", "")

            Log.d("ServerSync", "hostUrl - scheme: $scheme, host: $hostIp, isAlternativeUrl: $isAlternativeUrl")

            if (isAlternativeUrl && !alternativeUrl.isNullOrEmpty()) {
                try {
                    val uri = alternativeUrl.toUri()
                    hostIp = uri.host ?: hostIp
                    scheme = uri.scheme ?: scheme
                    Log.d("ServerSync", "hostUrl - using alternative URL, updated scheme: $scheme, host: $hostIp")
                } catch (e: Exception) {
                    Log.e("ServerSync", "hostUrl - failed to parse alternative URL: $alternativeUrl", e)
                    e.printStackTrace()
                }
            }

            val finalUrl = if (hostIp?.endsWith(".org") == true || hostIp?.endsWith(".gt") == true) {
                "$scheme://$hostIp/ml/"
            } else {
                "$scheme://$hostIp:5000/"
            }
            Log.d("ServerSync", "hostUrl returning: $finalUrl")
            return finalUrl
        }
    fun baseUrl(settings: SharedPreferences): String {
        val isAlternativeUrl = settings.getBoolean("isAlternativeUrl", false)
        var url = if (isAlternativeUrl) {
            settings.getString("processedAlternativeUrl", "")
        } else {
            settings.getString("couchdbURL", "")
        }
        if (url != null && url.endsWith("/db")) {
            url = url.removeSuffix("/db")
        }
        val sanitizedUrl = url?.replace(Regex("://[^:]+:[^@]+@"), "://***:***@") ?: "(empty)"
        Log.d("ServerSync", "baseUrl() - isAlternativeUrl: $isAlternativeUrl, URL: $sanitizedUrl")
        return url ?: ""
    }

    fun dbUrl(settings: SharedPreferences): String {
        val base = baseUrl(settings)
        return if (base.endsWith("/db")) base else "$base/db"
    }

    fun dbUrl(url: String): String {
        return if (url.endsWith("/db")) url else "$url/db"
    }

    fun getUrl(library: RealmMyLibrary?): String {
        return getUrl(library?.resourceId, library?.resourceLocalAddress)
    }

    fun getUrl(id: String?, file: String?): String {
        return "${getUrl()}/resources/$id/$file"
    }

    fun getUserImageUrl(userId: String?, imageName: String): String {
        return "${getUrl()}/_users/$userId/$imageName"
    }

    fun getUrl(): String {
        val settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val url = dbUrl(settings)
        val sanitizedUrl = url.replace(Regex("://[^:]+:[^@]+@"), "://***:***@")
        Log.d("ServerSync", "getUrl() returning: $sanitizedUrl")

        // Log all relevant configuration for debugging
        Log.d("ServerSync", "Configuration details:")
        Log.d("ServerSync", "  - serverURL: ${settings.getString("serverURL", "(not set)")}")
        Log.d("ServerSync", "  - url_Scheme: ${settings.getString("url_Scheme", "(not set)")}")
        Log.d("ServerSync", "  - url_Host: ${settings.getString("url_Host", "(not set)")}")
        Log.d("ServerSync", "  - url_Port: ${settings.getInt("url_Port", -1)}")
        Log.d("ServerSync", "  - isAlternativeUrl: ${settings.getBoolean("isAlternativeUrl", false)}")

        return url
    }

    fun getUpdateUrl(settings: SharedPreferences): String {
        val url = baseUrl(settings)
        return "$url/versions"
    }

    fun getChecksumUrl(settings: SharedPreferences): String {
        val url = baseUrl(settings)
        return "$url/fs/myPlanet.apk.sha256"
    }

    fun getHealthAccessUrl(settings: SharedPreferences): String {
        val url = baseUrl(settings)
        return String.format("%s/healthaccess?p=%s", url, settings.getString("serverPin", "0000"))
    }

    fun getApkVersionUrl(settings: SharedPreferences): String {
        val url = baseUrl(settings)
        return "$url/apkversion"
    }

    fun getApkUpdateUrl(path: String?): String {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val url = baseUrl(preferences)
        return "$url$path"
    }
}
