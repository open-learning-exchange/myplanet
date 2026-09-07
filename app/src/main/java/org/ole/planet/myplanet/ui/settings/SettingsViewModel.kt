package org.ole.planet.myplanet.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.RetryQueueDetails
import org.ole.planet.myplanet.repository.RetryRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configurationsRepository: ConfigurationsRepository,
    private val retryRepository: RetryRepository,
    private val resourcesRepository: ResourcesRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _clearDataEvent = Channel<Unit>(Channel.BUFFERED)
    val clearDataEvent: Flow<Unit> = _clearDataEvent.receiveAsFlow()

    private val _clearRetryQueueEvent = Channel<Boolean>(Channel.BUFFERED)
    val clearRetryQueueEvent: Flow<Boolean> = _clearRetryQueueEvent.receiveAsFlow()

    private val _retryQueueDetailsEvent = Channel<RetryQueueDetails>(Channel.BUFFERED)
    val retryQueueDetailsEvent: Flow<RetryQueueDetails> = _retryQueueDetailsEvent.receiveAsFlow()

    private val _downloadCompleteEvent = Channel<List<MyLibrary>>(Channel.BUFFERED)
    val downloadCompleteEvent: Flow<List<MyLibrary>> = _downloadCompleteEvent.receiveAsFlow()


    fun isCurrentlyProcessing(): Boolean {
        return retryRepository.isCurrentlyProcessing()
    }
    fun clearAllData() {
        viewModelScope.launch(dispatcherProvider.io) {
            configurationsRepository.clearAllData()
            configurationsRepository.clearPreferences()
            _clearDataEvent.send(Unit)
        }
    }

    fun clearRetryQueue() {
        viewModelScope.launch {
            val cleared = retryRepository.safeClearQueue()
            _clearRetryQueueEvent.send(cleared)
        }
    }


    fun fetchRetryQueueDetails() {
        viewModelScope.launch {
            _retryQueueDetailsEvent.send(retryRepository.getRetryQueueSnapshot())
        }
    }

    fun downloadFiles(libraryList: List<MyLibrary>?) {
        viewModelScope.launch {
            var files = libraryList
            try {
                files = resourcesRepository.downloadFiles(libraryList)
            } finally {
                _downloadCompleteEvent.send(files ?: emptyList())
            }
        }
    }
}
