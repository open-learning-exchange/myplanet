package org.ole.planet.myplanet.utils

/**
 * Identity of the installed app: its package, installed version, and the per-install device id
 * reported to the server. The Android implementation is [AndroidAppInfo].
 */
interface AppInfo {
    val packageName: String

    /** Installed version code, or 0 when it can't be read. */
    fun versionCode(): Int

    /** Installed version name, or null/empty when it can't be read. */
    fun versionName(): String?

    /** Stable per-install device id (Android's `ANDROID_ID`), or null when unavailable. */
    fun androidId(): String?
}
