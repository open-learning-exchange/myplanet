package org.ole.planet.myplanet.utilities

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME

object UrlUtils {
    fun header(context: Context): String {
        val settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return header(settings)
    }

    fun header(settings: SharedPreferences): String {
        val credentials = "${settings.getString("url_user", "")}:${settings.getString("url_pwd", "")}".toByteArray()
        return "Basic ${Base64.encodeToString(credentials, Base64.NO_WRAP)}"
    }

    fun hostUrl(context: Context): String {
        val settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return hostUrl(settings)
    }

    fun hostUrl(settings: SharedPreferences): String {
        var scheme = settings.getString("url_Scheme", "") ?: ""
        var hostIp = settings.getString("url_Host", "") ?: ""
        val isAlternativeUrl = settings.getBoolean("isAlternativeUrl", false)
        val alternativeUrl = settings.getString("processedAlternativeUrl", "")

        if (isAlternativeUrl && !alternativeUrl.isNullOrEmpty()) {
            try {
                val uri = Uri.parse(alternativeUrl)
                hostIp = uri.host ?: hostIp
                scheme = uri.scheme ?: scheme
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val builder = Uri.Builder().scheme(scheme)
        return if (hostIp.endsWith(".org") || hostIp.endsWith(".gt")) {
            builder.authority(hostIp).appendPath("ml").build().toString() + "/"
        } else {
            builder.authority("$hostIp:5000").build().toString() + "/"
        }
    }

    fun baseUrl(settings: SharedPreferences): String {
        val url = if (settings.getBoolean("isAlternativeUrl", false)) {
            settings.getString("processedAlternativeUrl", "")
        } else {
            settings.getString("couchdbURL", "")
        }
        return baseUrl(url ?: "")
    }

    fun dbUrl(settings: SharedPreferences): String {
        val base = baseUrl(settings)
        return dbUrl(base)
    }

    fun baseUrl(url: String): String {
        val uri = Uri.parse(url)
        val segments = uri.pathSegments
        return if (segments.lastOrNull() == "db") {
            val newPath = if (segments.size > 1) "/" + segments.dropLast(1).joinToString("/") else null
            uri.buildUpon().path(newPath).build().toString()
        } else {
            uri.toString()
        }
    }

    fun dbUrl(url: String): String {
        val uri = Uri.parse(url)
        return if (uri.pathSegments.lastOrNull() == "db") {
            uri.toString()
        } else {
            uri.buildUpon().appendPath("db").build().toString()
        }
    }

    fun getUrl(context: Context, library: RealmMyLibrary?): String {
        return getUrl(context, library?.resourceId, library?.resourceLocalAddress)
    }

    fun getUrl(context: Context, id: String?, file: String?): String {
        return Uri.parse(getUrl(context)).buildUpon()
            .appendPath("resources")
            .appendPath(id ?: "")
            .appendPath(file ?: "")
            .build().toString()
    }

    fun getUserImageUrl(context: Context, userId: String?, imageName: String): String {
        return Uri.parse(getUrl(context)).buildUpon()
            .appendPath("_users")
            .appendPath(userId ?: "")
            .appendPath(imageName)
            .build().toString()
    }

    fun getUrl(context: Context): String {
        val settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return dbUrl(settings)
    }

    fun getUrl(settings: SharedPreferences): String {
        return dbUrl(settings)
    }

    fun getUpdateUrl(settings: SharedPreferences): String {
        return Uri.parse(baseUrl(settings)).buildUpon()
            .appendPath("versions")
            .build().toString()
    }

    fun getChecksumUrl(settings: SharedPreferences): String {
        return Uri.parse(baseUrl(settings)).buildUpon()
            .appendPath("fs")
            .appendPath("myPlanet.apk.sha256")
            .build().toString()
    }

    fun getHealthAccessUrl(settings: SharedPreferences): String {
        return Uri.parse(baseUrl(settings)).buildUpon()
            .appendPath("healthaccess")
            .appendQueryParameter("p", settings.getString("serverPin", "0000"))
            .build().toString()
    }

    fun getApkVersionUrl(settings: SharedPreferences): String {
        return Uri.parse(baseUrl(settings)).buildUpon()
            .appendPath("apkversion")
            .build().toString()
    }

    fun getApkUpdateUrl(context: Context, path: String?): String {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return getApkUpdateUrl(preferences, path)
    }

    fun getApkUpdateUrl(settings: SharedPreferences, path: String?): String {
        val builder = Uri.parse(baseUrl(settings)).buildUpon()
        path?.let { builder.appendEncodedPath(it.removePrefix("/")) }
        return builder.build().toString()
    }
}
