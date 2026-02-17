package org.ole.planet.myplanet.services

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.utils.DownloadUtils
import javax.inject.Inject
import javax.inject.Singleton
import java.util.ArrayList

@Singleton
class ResourceDownloadCoordinator @Inject constructor(
    private val configurationsRepository: ConfigurationsRepository,
    @ApplicationContext private val context: Context
) {
    fun startBackgroundDownload(urls: ArrayList<String>) {
        MainApplication.applicationScope.launch {
            if (configurationsRepository.checkServerAvailability()) {
                if (urls.isNotEmpty()) {
                    DownloadUtils.openDownloadService(context, urls, false)
                }
            }
        }
    }
}
