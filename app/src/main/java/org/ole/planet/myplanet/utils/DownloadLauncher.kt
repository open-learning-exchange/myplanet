package org.ole.planet.myplanet.utils

/**
 * Queues resource URLs for the background downloader. The Android implementation is
 * [AndroidDownloadLauncher], which hands them to the download foreground service.
 */
interface DownloadLauncher {
    /** Queues [urls] ahead of any pending downloads and starts the downloader. */
    fun startPriorityDownloads(urls: List<String>)

    /** Queues [urls] behind any pending downloads and starts the downloader. */
    fun startDownloads(urls: List<String>, fromSync: Boolean)
}
