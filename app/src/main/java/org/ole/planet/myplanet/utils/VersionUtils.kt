package org.ole.planet.myplanet.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat.getLongVersionCode

object VersionUtils {
    private const val VERSION_PREFIX = "v"
    private const val LITE_SUFFIX = "-lite"

    fun getVersionCode(context: Context): Int {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return getLongVersionCode(pInfo).toInt()
            } else {
                @Suppress("DEPRECATION")
                return pInfo.versionCode
            }
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return 0
    }

    fun getVersionName(context: Context): String? {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            return pInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return ""
    }

    fun getAndroidId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    fun isVersionAllowed(currentVersion: String, minApkVersion: String): Boolean {
        return compareVersions(currentVersion, minApkVersion) >= 0
    }

    fun compareVersions(version1: String, version2: String): Int {
        val parts1 = VersionParts(version1, stripLiteSuffix = true)
        val parts2 = VersionParts(version2, stripLiteSuffix = false)
        var result = 0

        // Every part of both strings is read, even once the order is decided, so a
        // non-numeric part still throws NumberFormatException the way splitting did.
        while (parts1.hasNext || parts2.hasNext) {
            val comparison = when {
                parts1.hasNext && parts2.hasNext -> parts1.next().compareTo(parts2.next())
                parts1.hasNext -> { parts1.next(); 1 }
                else -> { parts2.next(); -1 }
            }
            if (result == 0) {
                result = comparison
            }
        }
        return result
    }

    /** Walks the dot-separated parts of a version string in place, without splitting it. */
    private class VersionParts(private val version: String, stripLiteSuffix: Boolean) {
        private val end =
            if (stripLiteSuffix && version.endsWith(LITE_SUFFIX)) version.length - LITE_SUFFIX.length
            else version.length
        private var cursor = if (version.startsWith(VERSION_PREFIX)) VERSION_PREFIX.length else 0

        /** A cursor past [end] means the last part has been consumed; an empty string still has one. */
        val hasNext: Boolean
            get() = cursor <= end

        fun next(): Int {
            val dot = version.indexOf('.', cursor)
            val stop = if (dot in cursor until end) dot else end
            val part = version.substring(cursor, stop).toInt()
            cursor = stop + 1
            return part
        }
    }

    fun parseApkVersionString(raw: String?): Int? {
        if (raw.isNullOrEmpty()) return null
        var vsn = raw.replace("v", "")
        vsn = vsn.replace(".", "")
        val cleaned = if (vsn.startsWith("0")) vsn.replaceFirst("0", "") else vsn
        return cleaned.toIntOrNull()
    }
}
