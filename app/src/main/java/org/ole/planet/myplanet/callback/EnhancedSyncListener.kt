package org.ole.planet.myplanet.callback

interface EnhancedSyncListener : SyncListener {
    fun onProgressUpdate(processName: String, itemsProcessed: Int)
    fun onDataReady(dataType: String)
}