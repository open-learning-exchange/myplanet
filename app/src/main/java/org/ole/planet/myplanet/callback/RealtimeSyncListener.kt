package org.ole.planet.myplanet.callback

data class TableDataUpdate(
    val table: String,
    val newItemsCount: Int,
    val updatedItemsCount: Int,
    val shouldRefreshUI: Boolean = true
)

interface RealtimeSyncListener : SyncListener {

    fun onTableDataUpdated(update: TableDataUpdate)
}

abstract class BaseRealtimeSyncListener : RealtimeSyncListener {
    
    override fun onSyncStarted() {
        // Default implementation - can be overridden
    }
    
    override fun onSyncComplete() {
        // Default implementation - can be overridden
    }

    override fun onSyncFailed(msg: String?) {
        // Default implementation - can be overridden
    }

    override fun onTableDataUpdated(update: TableDataUpdate) {
        // Default implementation - can be overridden
    }
}
