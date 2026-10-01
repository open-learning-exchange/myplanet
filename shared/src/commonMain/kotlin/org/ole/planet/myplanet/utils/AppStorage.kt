package org.ole.planet.myplanet.utils

/**
 * Where the app keeps downloaded content on the device. Paths are plain strings so callers in
 * shared code don't depend on a platform file type. The Android implementation is
 * [AndroidAppStorage].
 */
interface AppStorage {
    /** Absolute path of the app's external files directory, or null when storage is unavailable. */
    fun externalFilesDirPath(): String?

    /** Absolute `<external files dir>/ole/` path with a trailing slash, or "" when unavailable. */
    fun olePath(): String

    /** Whether the resource at [url] has been downloaded to its local path and is non-empty. */
    fun hasDownloadedFile(url: String?): Boolean
}
