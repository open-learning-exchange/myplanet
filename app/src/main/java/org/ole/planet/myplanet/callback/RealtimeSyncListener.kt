package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.TableDataUpdate

interface RealtimeSyncListener : OnSyncListener {

    fun onTableSyncStarted(table: String, totalItems: Int)

    fun onTableDataUpdated(update: TableDataUpdate)

    fun onTableSyncCompleted(table: String, itemsProcessed: Int, success: Boolean)
}
