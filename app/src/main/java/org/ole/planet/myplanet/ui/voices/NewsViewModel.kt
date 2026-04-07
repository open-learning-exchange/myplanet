package org.ole.planet.myplanet.ui.voices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.repository.ResourcesRepository

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val resourcesRepository: ResourcesRepository
) : ViewModel() {
    fun getPrivateImageUrlsCreatedAfter(timestamp: Long, callback: (List<String>) -> Unit) {
        viewModelScope.launch {
            val urls = resourcesRepository.getPrivateImageUrlsCreatedAfter(timestamp)
            callback(urls)
        }
    }
}
