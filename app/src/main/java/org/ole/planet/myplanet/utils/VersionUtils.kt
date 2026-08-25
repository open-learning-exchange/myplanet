package org.ole.planet.myplanet.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat.getLongVersionCode

import java.util.Collections
import java.util.LinkedHashMap

object VersionUtils {

    private val versionCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, List<Int>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Int>>): Boolean {
                return size > 50
            }
        }
    )

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
        val clean1 = version1.removeSuffix("-lite")
        val parts1 = versionCache.getOrPut(clean1) {
            clean1.removePrefix("v").split(".").map { it.toInt() }
        }

        val parts2 = versionCache.getOrPut(version2) {
            version2.removePrefix("v").split(".").map { it.toInt() }
        }

        for (i in 0 until kotlin.math.min(parts1.size, parts2.size)) {
            if (parts1[i] != parts2[i]) {
                return parts1[i].compareTo(parts2[i])
            }
        }
        return parts1.size.compareTo(parts2.size)
    }

    fun parseApkVersionString(raw: String?): Int? {
        if (raw.isNullOrEmpty()) return null
        var vsn = raw.replace("v", "")
        vsn = vsn.replace(".", "")
        val cleaned = if (vsn.startsWith("0")) vsn.replaceFirst("0", "") else vsn
        return cleaned.toIntOrNull()
    }
}
