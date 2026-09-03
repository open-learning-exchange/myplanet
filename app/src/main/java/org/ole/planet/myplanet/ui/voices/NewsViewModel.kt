package org.ole.planet.myplanet.ui.voices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val resourcesRepository: ResourcesRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _privateImageUrls = MutableSharedFlow<List<String>>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val privateImageUrls: SharedFlow<List<String>> = _privateImageUrls.asSharedFlow()

    fun getPrivateImageUrlsCreatedAfter(timestamp: Long) {
        viewModelScope.launch {
            val urls = resourcesRepository.getPrivateImageUrlsCreatedAfter(timestamp)
            _privateImageUrls.emit(urls)
        }
    }
}
