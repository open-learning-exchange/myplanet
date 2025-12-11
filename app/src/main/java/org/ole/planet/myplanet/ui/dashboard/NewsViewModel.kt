package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.repository.LibraryRepository
import javax.inject.Inject

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository
) : ViewModel() {
    fun getPrivateImageUrlsCreatedAfter(timestamp: Long, callback: (List<String>) -> Unit) {
        viewModelScope.launch {
            val urls = libraryRepository.getPrivateImageUrlsCreatedAfter(timestamp)
            callback(urls)
        }
    }
}
