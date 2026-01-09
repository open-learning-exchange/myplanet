package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.TableDataUpdate

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

    override fun onTableDataUpdated(update: TableDataUpdate) {
        // Default implementation - can be overridden
    }

    override fun onTableSyncCompleted(table: String, itemsProcessed: Int, success: Boolean) {
        // Default implementation - can be overridden
    }
}
