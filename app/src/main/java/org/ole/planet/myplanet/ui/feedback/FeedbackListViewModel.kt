package org.ole.planet.myplanet.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.repository.FeedbackRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.services.sync.SyncManager

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    object Complete : SyncState()
    data class Failed(val msg: String?) : SyncState()
}

@HiltViewModel
class FeedbackListViewModel @Inject constructor(
    private val feedbackRepository: FeedbackRepository,
    private val userSessionManager: UserSessionManager,
    private val syncManager: SyncManager,
    private val serverUrlMapper: ServerUrlMapper,
    private val sharedPrefManager: SharedPrefManager
) : ViewModel() {

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _feedbackList = MutableStateFlow<List<RealmFeedback>>(emptyList())
    val feedbackList: StateFlow<List<RealmFeedback>> = _feedbackList.asStateFlow()

    init {
        loadFeedback()
    }

    private fun loadFeedback() {
        viewModelScope.launch {
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
        val isFastSync = sharedPrefManager.getFastSync()
        if (isFastSync && !sharedPrefManager.isFeedbackSynced()) {
            checkServerAndStartSync()
        }
    }

    private fun checkServerAndStartSync() {
        val serverUrl = sharedPrefManager.getServerUrl()
        val mapping = serverUrlMapper.processUrl(serverUrl)

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            serverUrlMapper.updateServerIfNecessary(mapping, sharedPrefManager.rawPreferences) { url ->
                org.ole.planet.myplanet.MainApplication.isServerReachable(url)
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                startSyncManager()
            }
        }
    }

    private fun startSyncManager() {
        syncManager.start(object : org.ole.planet.myplanet.callback.OnSyncListener {
            override fun onSyncStarted() {
                _syncState.value = SyncState.Syncing
            }

            override fun onSyncComplete() {
                _syncState.value = SyncState.Complete
                sharedPrefManager.setFeedbackSynced(true)
                refreshFeedback()
            }

            override fun onSyncFailed(msg: String?) {
                _syncState.value = SyncState.Failed(msg)
            }
        }, "full", listOf("feedback"))
    }

    fun resetSyncState() {
        _syncState.value = SyncState.Idle
    }
}
