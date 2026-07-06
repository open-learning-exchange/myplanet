package org.ole.planet.myplanet.services.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    
    private val _dataUpdateFlow = MutableSharedFlow<TableDataUpdate>(extraBufferCapacity = 1)
    val dataUpdateFlow: SharedFlow<TableDataUpdate> = _dataUpdateFlow.asSharedFlow()

    fun notifyTableUpdated(update: TableDataUpdate) {
        _dataUpdateFlow.tryEmit(update)
    }

}
