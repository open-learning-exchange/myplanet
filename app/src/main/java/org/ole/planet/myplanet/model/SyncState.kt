package org.ole.planet.myplanet.model

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    object Success : SyncState()
    data class Failed(val message: String?) : SyncState()
}
