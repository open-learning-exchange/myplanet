package org.ole.planet.myplanet.service.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.ole.planet.myplanet.callback.RealtimeSyncListener
import org.ole.planet.myplanet.callback.TableDataUpdate

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealtimeSyncCoordinator @Inject constructor() {
    
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
    
}
