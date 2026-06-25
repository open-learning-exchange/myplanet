package org.ole.planet.myplanet.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmRetryOperation
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.services.ResourceDownloadCoordinator
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.retry.RetryQueue
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.DownloadUtils.downloadAllFiles
import javax.inject.Inject

data class RetryQueueDetails(
    val pendingCount: Long = 0,
    val pendingOps: List<RealmRetryOperation> = emptyList(),
    val isProcessing: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configurationsRepository: ConfigurationsRepository,
    private val sharedPrefManager: SharedPrefManager,
    private val retryQueue: RetryQueue,
    private val resourcesRepository: ResourcesRepository,
    private val resourceDownloadCoordinator: ResourceDownloadCoordinator,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _clearDataEvent = Channel<Unit>(Channel.BUFFERED)
    val clearDataEvent: Flow<Unit> = _clearDataEvent.receiveAsFlow()

    private val _clearRetryQueueEvent = Channel<Boolean>(Channel.BUFFERED)
    val clearRetryQueueEvent: Flow<Boolean> = _clearRetryQueueEvent.receiveAsFlow()

    private val _retryQueueDetailsEvent = Channel<RetryQueueDetails>(Channel.BUFFERED)
    val retryQueueDetailsEvent: Flow<RetryQueueDetails> = _retryQueueDetailsEvent.receiveAsFlow()

    private val _downloadCompleteEvent = Channel<List<RealmMyLibrary>>(Channel.BUFFERED)
    val downloadCompleteEvent: Flow<List<RealmMyLibrary>> = _downloadCompleteEvent.receiveAsFlow()


    fun isCurrentlyProcessing(): Boolean {
        return retryQueue.isCurrentlyProcessing()
    }
    fun clearAllData() {
        viewModelScope.launch(dispatcherProvider.io) {
            configurationsRepository.clearAllData()
            sharedPrefManager.clearPreferences()
            _clearDataEvent.send(Unit)
        }
    }

    fun clearRetryQueue() {
        viewModelScope.launch(dispatcherProvider.io) {
            val cleared = retryQueue.safeClearQueue()
            _clearRetryQueueEvent.send(cleared)
        }
    }

    fun fetchRetryQueueDetails() {
        viewModelScope.launch(dispatcherProvider.io) {
            val pendingCount = retryQueue.getPendingCount()
            val pendingOps = retryQueue.getPendingOperations()
            val isProcessing = retryQueue.isCurrentlyProcessing()
            _retryQueueDetailsEvent.send(RetryQueueDetails(pendingCount, pendingOps, isProcessing))
        }
    }

    fun downloadFiles(libraryList: List<RealmMyLibrary>?) {
        viewModelScope.launch(dispatcherProvider.io) {
            var files = libraryList
            try {
                files = libraryList ?: resourcesRepository.getAllLibrariesToSync()
                resourceDownloadCoordinator.startBackgroundDownload(downloadAllFiles(files))
            } finally {
                _downloadCompleteEvent.send(files ?: emptyList())
            }
        }
    }
}
