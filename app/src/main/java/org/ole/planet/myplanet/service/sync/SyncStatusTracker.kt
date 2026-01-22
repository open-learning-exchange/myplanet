package org.ole.planet.myplanet.service.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.ole.planet.myplanet.callback.OnSyncListener
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncStatusTracker @Inject constructor() {
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus

    var listener: OnSyncListener? = null

    sealed class SyncStatus {
        object Idle : SyncStatus()
        object Syncing : SyncStatus()
        data class Success(val message: String) : SyncStatus()
        data class Error(val message: String) : SyncStatus()
    }

    fun setStatus(status: SyncStatus) {
        _syncStatus.value = status
    }

    fun notifyStarted() {
        listener?.onSyncStarted()
    }

    fun notifyComplete() {
        listener?.onSyncComplete()
    }

    fun notifyFailed(message: String?) {
        listener?.onSyncFailed(message)
    }
}
