package org.ole.planet.myplanet.utilities

import android.content.Context
import android.content.SharedPreferences
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME

object UrlUtils {
    fun baseUrl(settings: SharedPreferences): String {
        var url = if (settings.getBoolean("isAlternativeUrl", false)) {
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

    fun baseUrl(url: String): String {
        return if (url.endsWith("/db")) url.removeSuffix("/db") else url
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
        return dbUrl(settings)
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
