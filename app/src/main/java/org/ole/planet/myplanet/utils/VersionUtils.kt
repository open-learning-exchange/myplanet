package org.ole.planet.myplanet.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat.getLongVersionCode

object VersionUtils {
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
        val e1 = if (version1.endsWith("-lite")) version1.length - 5 else version1.length
        val s1 = if (version1.startsWith("v")) 1 else 0
        val e2 = version2.length
        val s2 = if (version2.startsWith("v")) 1 else 0

        var i1 = s1
        var i2 = s2
        var count1 = 0
        var count2 = 0

        var diff = 0

        while (i1 <= e1 || i2 <= e2) {
            val hasPart1 = i1 <= e1
            val hasPart2 = i2 <= e2

            var part1 = 0
            if (hasPart1) {
                var dot1 = version1.indexOf('.', i1)
                if (dot1 < 0 || dot1 > e1) dot1 = e1
                part1 = version1.substring(i1, dot1).toInt()
                i1 = dot1 + 1
                count1++
            }

            var part2 = 0
            if (hasPart2) {
                var dot2 = version2.indexOf('.', i2)
                if (dot2 < 0 || dot2 > e2) dot2 = e2
                part2 = version2.substring(i2, dot2).toInt()
                i2 = dot2 + 1
                count2++
            }

            if (hasPart1 && hasPart2 && diff == 0 && part1 != part2) {
                diff = part1.compareTo(part2)
            }
        }

        return if (diff != 0) diff else count1.compareTo(count2)
    }

    fun parseApkVersionString(raw: String?): Int? {
        if (raw.isNullOrEmpty()) return null
        var vsn = raw.replace("v", "")
        vsn = vsn.replace(".", "")
        val cleaned = if (vsn.startsWith("0")) vsn.replaceFirst("0", "") else vsn
        return cleaned.toIntOrNull()
    }
}
