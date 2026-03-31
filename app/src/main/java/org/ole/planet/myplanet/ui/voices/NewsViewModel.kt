package org.ole.planet.myplanet.ui.voices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val resourcesRepository: ResourcesRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _privateImageUrls = MutableStateFlow<List<String>>(emptyList())
    val privateImageUrls: StateFlow<List<String>> = _privateImageUrls.asStateFlow()

    fun getPrivateImageUrlsCreatedAfter(timestamp: Long) {
        viewModelScope.launch {
            val urls = withContext(dispatcherProvider.io) {
                resourcesRepository.getPrivateImageUrlsCreatedAfter(timestamp)
            }
            _privateImageUrls.value = urls
        }
    }
}
