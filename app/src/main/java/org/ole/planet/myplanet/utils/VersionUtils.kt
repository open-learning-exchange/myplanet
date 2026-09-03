package org.ole.planet.myplanet.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.pm.PackageInfoCompat.getLongVersionCode

object VersionUtils {
    private const val TAG = "VersionUtils"

    @Volatile
    private var cachedAndroidId: String? = null

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
            Log.w(TAG, "Failed to get version code", e)
        }
        return 0
    }

    fun getVersionName(context: Context): String? {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            return pInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Failed to get version name", e)
        }
        return ""
    }

    fun getAndroidId(context: Context): String? {
        cachedAndroidId?.let { return it }
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        if (id != null) {
            cachedAndroidId = id
        }
        return id
    }

    @VisibleForTesting
    internal fun resetAndroidIdCacheForTesting() {
        cachedAndroidId = null
    }

    fun isVersionAllowed(currentVersion: String, minApkVersion: String): Boolean {
        return compareVersions(currentVersion, minApkVersion) >= 0
    }

    fun compareVersions(version1: String, version2: String): Int {
        val parts1 = parseIntSegments(version1.removeSuffix("-lite").removePrefix("v"))
        val parts2 = parseIntSegments(version2.removePrefix("v"))

        for (i in 0 until minOf(parts1.size, parts2.size)) {
            if (parts1[i] != parts2[i]) {
                return parts1[i].compareTo(parts2[i])
            }
        }
        return parts1.size.compareTo(parts2.size)
    }

    private fun parseIntSegments(version: String): IntArray {
        if (version.isEmpty()) {
            throw NumberFormatException(version)
        }
        val segments = mutableListOf<Int>()
        var start = 0
        while (start <= version.length) {
            val end = version.indexOf('.', start)
            val next = if (end == -1) version.length else end
            segments.add(version.substring(start, next).toInt())
            if (end == -1) break
            start = end + 1
        }
        return segments.toIntArray()
    }

    fun parseApkVersionString(raw: String?): Int? {
        if (raw.isNullOrEmpty()) return null
        var vsn = raw.replace("v", "")
        vsn = vsn.replace(".", "")
        val cleaned = if (vsn.startsWith("0")) vsn.replaceFirst("0", "") else vsn
        return cleaned.toIntOrNull()
    }
}
