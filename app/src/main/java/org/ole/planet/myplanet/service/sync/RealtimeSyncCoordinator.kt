package org.ole.planet.myplanet.service.sync

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.ole.planet.myplanet.callback.RealtimeSyncListener
import org.ole.planet.myplanet.callback.SyncProgressUpdate
import org.ole.planet.myplanet.callback.TableDataUpdate
import java.util.concurrent.ConcurrentHashMap

class RealtimeSyncCoordinator {
    
    companion object {
        @Volatile
        private var INSTANCE: RealtimeSyncCoordinator? = null
        
        fun getInstance(): RealtimeSyncCoordinator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RealtimeSyncCoordinator().also { INSTANCE = it }
            }
        }
    }
    
    private val listeners = mutableSetOf<RealtimeSyncListener>()
    private val tableProgress = ConcurrentHashMap<String, SyncProgressUpdate>()
    
    private val _syncProgressFlow = MutableSharedFlow<SyncProgressUpdate>()
    val syncProgressFlow: SharedFlow<SyncProgressUpdate> = _syncProgressFlow.asSharedFlow()
    
    private val _dataUpdateFlow = MutableSharedFlow<TableDataUpdate>()
    val dataUpdateFlow: SharedFlow<TableDataUpdate> = _dataUpdateFlow.asSharedFlow()
    
    fun addListener(listener: RealtimeSyncListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }
    
    fun removeListener(listener: RealtimeSyncListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }
    
    fun notifyTableSyncStarted(table: String, totalItems: Int) {
        val update = SyncProgressUpdate(
            table = table,
            itemsProcessed = 0,
            totalItems = totalItems,
            percentage = 0f,
            message = "Starting sync for $table",
            isComplete = false
        )
        tableProgress[table] = update
        
        synchronized(listeners) {
            listeners.forEach { it.onTableSyncStarted(table, totalItems) }
        }
        _syncProgressFlow.tryEmit(update)
    }
    
    fun notifyTableSyncProgress(table: String, itemsProcessed: Int, totalItems: Int, message: String? = null) {
        val percentage = if (totalItems > 0) (itemsProcessed.toFloat() / totalItems.toFloat()) * 100f else 0f
        val update = SyncProgressUpdate(
            table = table,
            itemsProcessed = itemsProcessed,
            totalItems = totalItems,
            percentage = percentage,
            message = message ?: "Syncing $table: $itemsProcessed/$totalItems",
            isComplete = false
        )
        tableProgress[table] = update
        
        synchronized(listeners) {
            listeners.forEach { it.onTableSyncProgress(update) }
        }
        _syncProgressFlow.tryEmit(update)
    }
    
    fun notifyTableDataUpdated(table: String, newItemsCount: Int, updatedItemsCount: Int) {
        Log.d("RealtimeSyncCoordinator", "=== notifyTableDataUpdated ===")
        Log.d("RealtimeSyncCoordinator", "Table: $table, newItems: $newItemsCount, updatedItems: $updatedItemsCount")
        Log.d("RealtimeSyncCoordinator", "Active listeners count: ${listeners.size}")
        
        val update = TableDataUpdate(
            table = table,
            newItemsCount = newItemsCount,
            updatedItemsCount = updatedItemsCount,
            shouldRefreshUI = true
        )
        
        synchronized(listeners) {
            listeners.forEach { 
                Log.d("RealtimeSyncCoordinator", "Notifying listener: ${it.javaClass.simpleName}")
                it.onTableDataUpdated(update) 
            }
        }
        _dataUpdateFlow.tryEmit(update)
        Log.d("RealtimeSyncCoordinator", "=== notifyTableDataUpdated END ===")
    }
    
    fun notifyTableSyncCompleted(table: String, itemsProcessed: Int, success: Boolean) {
        val update = SyncProgressUpdate(
            table = table,
            itemsProcessed = itemsProcessed,
            totalItems = tableProgress[table]?.totalItems ?: itemsProcessed,
            percentage = 100f,
            message = if (success) "Completed $table sync" else "Failed $table sync",
            isComplete = true
        )
        tableProgress[table] = update
        
        synchronized(listeners) {
            listeners.forEach { it.onTableSyncCompleted(table, itemsProcessed, success) }
        }
        _syncProgressFlow.tryEmit(update)
    }
    
    fun notifyBatchProcessed(table: String, batchNumber: Int, itemsInBatch: Int) {
        synchronized(listeners) {
            listeners.forEach { it.onBatchProcessed(table, batchNumber, itemsInBatch) }
        }
    }
    
    fun getCurrentProgress(table: String): SyncProgressUpdate? {
        return tableProgress[table]
    }
    
    fun getAllProgress(): Map<String, SyncProgressUpdate> {
        return tableProgress.toMap()
    }
    
    fun clearProgress() {
        tableProgress.clear()
    }
    
    fun clearTableProgress(table: String) {
        tableProgress.remove(table)
    }
}