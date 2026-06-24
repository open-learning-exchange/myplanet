package org.ole.planet.myplanet.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    private val _clearDataEvent = MutableSharedFlow<Unit>()
    val clearDataEvent: SharedFlow<Unit> = _clearDataEvent.asSharedFlow()

    private val _clearRetryQueueEvent = MutableSharedFlow<Boolean>()
    val clearRetryQueueEvent: SharedFlow<Boolean> = _clearRetryQueueEvent.asSharedFlow()

    private val _retryQueueDetailsEvent = MutableSharedFlow<RetryQueueDetails>()
    val retryQueueDetailsEvent: SharedFlow<RetryQueueDetails> = _retryQueueDetailsEvent.asSharedFlow()

    private val _downloadCompleteEvent = MutableSharedFlow<List<RealmMyLibrary>>()
    val downloadCompleteEvent: SharedFlow<List<RealmMyLibrary>> = _downloadCompleteEvent.asSharedFlow()

    fun clearAllData() {
        viewModelScope.launch(dispatcherProvider.io) {
            configurationsRepository.clearAllData()
            sharedPrefManager.clearPreferences()
            _clearDataEvent.emit(Unit)
        }
    }

    fun clearRetryQueue() {
        viewModelScope.launch {
            val cleared = retryQueue.safeClearQueue()
            _clearRetryQueueEvent.emit(cleared)
        }
    }

    fun fetchRetryQueueDetails() {
        viewModelScope.launch {
            val pendingCount = retryQueue.getPendingCount()
            val pendingOps = retryQueue.getPendingOperations()
            val isProcessing = retryQueue.isCurrentlyProcessing()
            _retryQueueDetailsEvent.emit(RetryQueueDetails(pendingCount, pendingOps, isProcessing))
        }
    }

    fun downloadFiles(libraryList: List<RealmMyLibrary>?) {
        viewModelScope.launch {
            var files = libraryList
            try {
                files = libraryList ?: resourcesRepository.getAllLibrariesToSync()
                resourceDownloadCoordinator.startBackgroundDownload(downloadAllFiles(files))
            } finally {
                _downloadCompleteEvent.emit(files ?: emptyList())
            }
        }
    }
}
