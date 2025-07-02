package org.ole.planet.myplanet.utilities

import android.content.SharedPreferences

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
}
