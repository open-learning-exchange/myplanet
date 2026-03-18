package org.ole.planet.myplanet.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat.getLongVersionCode

object VersionUtils {
    @JvmStatic
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

    @JvmStatic
    fun getVersionName(context: Context): String? {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            return pInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return ""
    }

    @JvmStatic
    fun getAndroidId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    @JvmStatic
    fun isVersionAllowed(currentVersion: String, minApkVersion: String): Boolean {
        return compareVersions(currentVersion, minApkVersion) >= 0
    }

    @JvmStatic
    fun compareVersions(version1: String, version2: String): Int {
        val parts1 = version1.removeSuffix("-lite").removePrefix("v").split(".").map { it.toInt() }
        val parts2 = version2.removePrefix("v").split(".").map { it.toInt() }

        for (i in 0 until kotlin.math.min(parts1.size, parts2.size)) {
            if (parts1[i] != parts2[i]) {
                return parts1[i].compareTo(parts2[i])
            }
        }
        return parts1.size.compareTo(parts2.size)
    }

    @JvmStatic
    fun parseApkVersionString(raw: String?): Int? {
        if (raw.isNullOrEmpty()) return null
        var vsn = raw.replace("v".toRegex(), "")
        vsn = vsn.replace("\\.".toRegex(), "")
        val cleaned = if (vsn.startsWith("0")) vsn.replaceFirst("0", "") else vsn
        return cleaned.toIntOrNull()
    }
}
