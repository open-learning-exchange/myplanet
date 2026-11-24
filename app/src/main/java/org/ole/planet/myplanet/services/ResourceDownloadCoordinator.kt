package org.ole.planet.myplanet.services

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.ArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.DownloadUtils
import org.ole.planet.myplanet.utils.FileUtils

@Singleton
class ResourceDownloadCoordinator @Inject constructor(
    private val configurationsRepository: ConfigurationsRepository,
    private val resourcesRepository: ResourcesRepository,
    private val prefData: SharedPrefManager,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    fun startBackgroundDownload(urls: ArrayList<String>) {
        startDownload(urls, fromSync = false)
    }

    /**
     * The beta "auto-download resources" option. Queued as sync-owned so the "Downloads during sync"
     * settings (Wi-Fi only, size limit) apply to it like to any other download the user did not start.
     */
    fun startAutoDownload(urls: ArrayList<String>) {
        startDownload(urls, fromSync = true)
    }

    private fun startDownload(urls: ArrayList<String>, fromSync: Boolean) {
        applicationScope.launch {
            if (configurationsRepository.checkServerAvailability()) {
                if (urls.isNotEmpty()) {
                    DownloadUtils.openDownloadService(context, urls, fromSync)
                }
            }
        }
    }

    suspend fun downloadIfMissing(url: String) {
        withContext(dispatcherProvider.io) {
            if (!FileUtils.checkFileExist(context, url)) {
                DownloadUtils.openDownloadService(context, arrayListOf(url), false)
            }
        }
    }

    suspend fun runPostSyncDownloads() {
        withContext(dispatcherProvider.io) {
            val links = configurationsRepository.getQueuedDownloads()
            if (links.isNotEmpty()) {
                DownloadUtils.openDownloadService(context, ArrayList(links), true)
            }

            val betaAutoDownload = prefData.getBetaAutoDownload()
            if (betaAutoDownload) {
                startAutoDownload(
                    DownloadUtils.downloadAllFiles(resourcesRepository.getAllLibrariesToSync())
                )
            }
        }
    }
}
