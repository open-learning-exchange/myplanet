package org.ole.planet.myplanet.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.repository.FeedbackRepository
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.SyncManager
import org.ole.planet.myplanet.utils.DispatcherProvider

@HiltViewModel
class FeedbackListViewModel @Inject constructor(
    private val feedbackRepository: FeedbackRepository,
    private val userSessionManager: UserSessionManager,
    private val dispatcherProvider: DispatcherProvider,
    private val syncManager: SyncManager
) : ViewModel() {

    sealed class SyncStatus {
        object Idle : SyncStatus()
        object Syncing : SyncStatus()
        object Success : SyncStatus()
        data class Error(val message: String) : SyncStatus()
    }

    private val _feedbackList = MutableStateFlow<List<RealmFeedback>>(emptyList())
    val feedbackList: StateFlow<List<RealmFeedback>> = _feedbackList.asStateFlow()

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private var fetchJob: Job? = null

    init {
        loadFeedback()
    }

    private fun loadFeedback() {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch(dispatcherProvider.io) {
            val user = userSessionManager.getUserModel()
            feedbackRepository.getFeedback(user).collectLatest { feedback ->
                _feedbackList.value = feedback
            }
        }
    }
    fun refreshFeedback() {
        loadFeedback()
    }

    fun startFeedbackSync() {
        syncManager.start(object : OnSyncListener {
            override fun onSyncStarted() {
                _syncStatus.value = SyncStatus.Syncing
            }

            override fun onSyncComplete() {
                _syncStatus.value = SyncStatus.Success
            }

            override fun onSyncFailed(msg: String?) {
                _syncStatus.value = SyncStatus.Error(msg ?: "Unknown error")
            }
        }, "full", listOf("feedback"))
    }
}
