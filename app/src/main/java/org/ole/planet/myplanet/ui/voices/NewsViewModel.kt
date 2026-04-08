package org.ole.planet.myplanet.ui.voices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val resourcesRepository: ResourcesRepository,
    private val voicesRepository: VoicesRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _privateImageUrls = MutableSharedFlow<List<String>>()
    val privateImageUrls: SharedFlow<List<String>> = _privateImageUrls.asSharedFlow()

    fun getPrivateImageUrlsCreatedAfter(timestamp: Long) {
        viewModelScope.launch {
            val urls = withContext(dispatcherProvider.io) {
                resourcesRepository.getPrivateImageUrlsCreatedAfter(timestamp)
            }
            _privateImageUrls.emit(urls)
        }
    }

    suspend fun observeCommunityNews(userIdentifier: String): Flow<List<RealmNews>> {
        return voicesRepository.getCommunityNews(userIdentifier)
    }
}
