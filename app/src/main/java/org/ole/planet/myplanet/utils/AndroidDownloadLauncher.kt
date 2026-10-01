package org.ole.planet.myplanet.utils

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidDownloadLauncher @Inject constructor(
    @param:ApplicationContext private val context: Context
) : DownloadLauncher {
    override fun startPriorityDownloads(urls: List<String>) {
        DownloadUtils.openPriorityDownloadService(context, ArrayList(urls))
    }

    override fun startDownloads(urls: List<String>, fromSync: Boolean) {
        DownloadUtils.openDownloadService(context, ArrayList(urls), fromSync)
    }
}
