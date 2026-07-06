package org.ole.planet.myplanet.services.sync

import java.util.concurrent.CopyOnWriteArraySet
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.ole.planet.myplanet.callback.OnRealtimeSyncListener
import org.ole.planet.myplanet.model.TableDataUpdate

class RealtimeSyncManager {
    companion object {
        @Volatile
        private var INSTANCE: RealtimeSyncManager? = null
        
        fun getInstance(): RealtimeSyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RealtimeSyncManager().also { INSTANCE = it }
            }
        }
    }
    
    private val listeners = CopyOnWriteArraySet<OnRealtimeSyncListener>()
    private val _dataUpdateFlow = MutableSharedFlow<TableDataUpdate>(extraBufferCapacity = 1)
    val dataUpdateFlow: SharedFlow<TableDataUpdate> = _dataUpdateFlow.asSharedFlow()
    
    fun addListener(listener: OnRealtimeSyncListener) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: OnRealtimeSyncListener) {
        listeners.remove(listener)
    }

    fun notifyTableUpdated(update: TableDataUpdate) {
        listeners.forEach { it.onTableDataUpdated(update) }
        _dataUpdateFlow.tryEmit(update)
    }

}
