package org.ole.planet.myplanet.ui.viewer

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.ole.planet.myplanet.data.auth.AuthSessionUpdater
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

@HiltViewModel
class ResourceViewerViewModel @Inject constructor(
    private val resourcesRepository: ResourcesRepository,
    private val authSessionUpdaterFactory: AuthSessionUpdater.Factory,
    private val configurationsRepository: ConfigurationsRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    suspend fun ensureServerUrlUpdated() {
        configurationsRepository.ensureServerUrlUpdated()
    }

    fun getAuthSessionUpdater(callback: AuthSessionUpdater.AuthCallback): AuthSessionUpdater {
        return authSessionUpdaterFactory.create(callback)
    }

    suspend fun getLibraryItemById(id: String): MyLibrary? {
        return resourcesRepository.getLibraryItemById(id)
    }

    suspend fun updateLibraryItemTranslationAudioPath(id: String, outputFile: String?) {
        resourcesRepository.updateLibraryItem(id) { it.translationAudioPath = outputFile }
    }
}
