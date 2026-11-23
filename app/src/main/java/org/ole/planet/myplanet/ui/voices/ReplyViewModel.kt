package org.ole.planet.myplanet.ui.voices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.VoicesRepository

@HiltViewModel
class ReplyViewModel @Inject constructor(
    private val voicesRepository: VoicesRepository,
    private val resourcesRepository: ResourcesRepository,
) : ViewModel() {

    private var requestedResourceIds = emptySet<String>()

    suspend fun getNewsWithReplies(newsId: String): Pair<News?, List<News>> {
        return voicesRepository.getNewsWithReplies(newsId)
    }

    fun downloadReferencedResources(list: List<News?>) {
        val resourceIds = VoicesAdapterHelper.referencedResourceIds(list) - requestedResourceIds
        if (resourceIds.isEmpty()) return
        requestedResourceIds = requestedResourceIds + resourceIds
        viewModelScope.launch {
            resourcesRepository.downloadResources(resourcesRepository.getLibraryItemsByResourceIds(resourceIds))
        }
    }
}
