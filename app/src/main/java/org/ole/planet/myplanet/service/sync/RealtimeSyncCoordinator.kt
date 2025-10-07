package org.ole.planet.myplanet.service.sync

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.ole.planet.myplanet.callback.RealtimeSyncListener
import org.ole.planet.myplanet.callback.TableDataUpdate

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

}
