package org.ole.planet.myplanet.utilities

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
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


            if (isAlternativeUrl && !alternativeUrl.isNullOrEmpty()) {
                try {
                    val uri = alternativeUrl.toUri()
                    hostIp = uri.host ?: hostIp
                    scheme = uri.scheme ?: scheme
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val finalUrl = if (hostIp?.endsWith(".org") == true || hostIp?.endsWith(".gt") == true) {
                "$scheme://$hostIp/ml/"
            } else {
                "$scheme://$hostIp:5000/"
            }
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
