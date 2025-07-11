package org.ole.planet.myplanet.callback

// 1. Enhanced SyncListener interface
interface EnhancedSyncListener : SyncListener {
    fun onProgressUpdate(processName: String, itemsProcessed: Int)
    fun onDataReady(dataType: String) // Called when specific data type is ready
}