package org.ole.planet.myplanet.callback

data class SyncProgressUpdate(
    val table: String,
    val itemsProcessed: Int,
    val totalItems: Int,
    val percentage: Float,
    val message: String,
    val isComplete: Boolean = false
)

data class TableDataUpdate(
    val table: String,
    val newItemsCount: Int,
    val updatedItemsCount: Int,
    val shouldRefreshUI: Boolean = true
)

interface RealtimeSyncListener : SyncListener {
    
    fun onTableSyncStarted(table: String, totalItems: Int)
    
    fun onTableSyncProgress(update: SyncProgressUpdate)
    
    fun onTableDataUpdated(update: TableDataUpdate)
    
    fun onTableSyncCompleted(table: String, itemsProcessed: Int, success: Boolean)
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
    
    override fun onTableSyncStarted(table: String, totalItems: Int) {
        // Default implementation - can be overridden
    }
    
    override fun onTableSyncProgress(update: SyncProgressUpdate) {
        // Default implementation - can be overridden
    }
    
    override fun onTableDataUpdated(update: TableDataUpdate) {
        // Default implementation - can be overridden
    }
    
    override fun onTableSyncCompleted(table: String, itemsProcessed: Int, success: Boolean) {
        // Default implementation - can be overridden
    }
}
