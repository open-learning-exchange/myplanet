package org.ole.planet.myplanet.ui.feedback

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.repository.FeedbackRepository
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.services.sync.SyncManager

@HiltViewModel
class FeedbackListViewModel @Inject constructor(
    private val feedbackRepository: FeedbackRepository,
    private val userSessionManager: UserSessionManager,
    private val syncManager: SyncManager,
    private val serverUrlMapper: ServerUrlMapper,
    @AppPreferences private val sharedPreferences: SharedPreferences
) : ViewModel() {

    private val _feedbackList = MutableStateFlow<List<RealmFeedback>>(emptyList())
    val feedbackList: StateFlow<List<RealmFeedback>> = _feedbackList.asStateFlow()

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus

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
        val isFastSync = sharedPreferences.getBoolean("fastSync", false)
        val isFeedbackSynced = sharedPreferences.getBoolean("feedback_synced", false)

        if (isFastSync && !isFeedbackSynced) {
            viewModelScope.launch(Dispatchers.IO) {
                val serverUrl = sharedPreferences.getString("serverURL", "") ?: ""
                val mapping = serverUrlMapper.processUrl(serverUrl)
                serverUrlMapper.updateServerIfNecessary(mapping, sharedPreferences) { url ->
                    MainApplication.isServerReachable(url)
                }
                withContext(Dispatchers.Main) {
                    startSyncManager()
                }
            }
        }
    }

    private fun startSyncManager() {
        syncManager.start(object : OnSyncListener {
            override fun onSyncStarted() {
                _syncStatus.value = SyncStatus.Syncing
            }

            override fun onSyncComplete() {
                _syncStatus.value = SyncStatus.Success("Sync completed")
                sharedPreferences.edit().putBoolean("feedback_synced", true).apply()
                refreshFeedback()
            }

            override fun onSyncFailed(msg: String?) {
                _syncStatus.value = SyncStatus.Error(msg ?: "Unknown error")
            }
        }, "full", listOf("feedback"))
    }

    sealed class SyncStatus {
        object Idle : SyncStatus()
        object Syncing : SyncStatus()
        data class Success(val message: String) : SyncStatus()
        data class Error(val message: String) : SyncStatus()
    }
}
