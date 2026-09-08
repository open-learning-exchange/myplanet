package org.ole.planet.myplanet.services

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.ArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.utils.DownloadUtils

@Singleton
class ResourceDownloadCoordinator @Inject constructor(
    private val configurationsRepository: ConfigurationsRepository,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    fun startBackgroundDownload(urls: ArrayList<String>) {
        applicationScope.launch {
            if (configurationsRepository.checkServerAvailability()) {
                if (urls.isNotEmpty()) {
                    DownloadUtils.openDownloadService(context, urls, false)
                }
            }
        }
    }
}
