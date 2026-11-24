package org.ole.planet.myplanet.services

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data-saving rules for files that sync queues on its own (sync-owned downloads), as opposed to files the
 * user asked for. Both [DownloadService] and [DownloadWorker] consult it so the two download paths agree.
 */
@Singleton
class SyncDownloadPolicy @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPrefManager: SharedPrefManager,
) {
    /** True while the user wants sync downloads on Wi-Fi only and the active connection is metered. */
    fun mustWaitForWifi(): Boolean = sharedPrefManager.isSyncDownloadWifiOnly() && isOnMeteredNetwork()

    private fun isOnMeteredNetwork(): Boolean {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return connectivityManager.isActiveNetworkMetered
    }

    companion object {
        fun isSyncOwned(downloadPreferences: SharedPreferences, url: String): Boolean =
            downloadPreferences.getStringSet(DownloadService.SYNC_DOWNLOADS_KEY, emptySet())?.contains(url) == true
    }
}
