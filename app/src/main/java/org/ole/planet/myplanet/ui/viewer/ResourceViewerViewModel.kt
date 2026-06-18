package org.ole.planet.myplanet.ui.viewer

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.ole.planet.myplanet.model.RealmMyLibrary
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.auth.AuthSessionUpdater
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.DispatcherProvider
import javax.inject.Inject

@HiltViewModel
class ResourceViewerViewModel @Inject constructor(
    private val resourcesRepository: ResourcesRepository,
    private val authSessionUpdaterFactory: AuthSessionUpdater.Factory,
    private val serverUrlMapper: ServerUrlMapper,
    private val sharedPrefManager: SharedPrefManager,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    suspend fun ensureServerUrlUpdated() {
        val serverUrl = sharedPrefManager.getServerUrl()
        val mapping = serverUrlMapper.processUrl(serverUrl)
        if (mapping.alternativeUrl != null) {
            serverUrlMapper.updateServerIfNecessary(mapping, sharedPrefManager.rawPreferences) { url ->
                isUrlDirectlyReachable(url)
            }
        }
    }

    private suspend fun isUrlDirectlyReachable(url: String): Boolean {
        return try {
            withContext(dispatcherProvider.io) {
                val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "http://$url" else url
                val connection = java.net.URL(cleanUrl).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "GET"
                val code = connection.responseCode
                connection.disconnect()
                code in 200..599
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getAuthSessionUpdater(callback: AuthSessionUpdater.AuthCallback): AuthSessionUpdater {
        return authSessionUpdaterFactory.create(callback)
    }

    suspend fun getLibraryItemById(id: String): RealmMyLibrary? {
        return resourcesRepository.getLibraryItemById(id)
    }

    suspend fun updateLibraryItemTranslationAudioPath(id: String, outputFile: String?) {
        resourcesRepository.updateLibraryItem(id) { it.translationAudioPath = outputFile }
    }
}
