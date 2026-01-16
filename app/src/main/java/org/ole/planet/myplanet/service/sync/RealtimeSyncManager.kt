package org.ole.planet.myplanet.service.sync

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
    
    private val listeners = mutableSetOf<OnRealtimeSyncListener>()
    private val _dataUpdateFlow = MutableSharedFlow<TableDataUpdate>()
    val dataUpdateFlow: SharedFlow<TableDataUpdate> = _dataUpdateFlow.asSharedFlow()
    
    fun addListener(listener: OnRealtimeSyncListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }
    
    fun removeListener(listener: OnRealtimeSyncListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }
    
}
