package org.ole.planet.myplanet.services.sync

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.ole.planet.myplanet.model.TableDataUpdate

@Singleton
class RealtimeSyncManager @Inject constructor() {
    
    private val _dataUpdateFlow = MutableSharedFlow<TableDataUpdate>(extraBufferCapacity = 1)
    val dataUpdateFlow: SharedFlow<TableDataUpdate> = _dataUpdateFlow.asSharedFlow()

    fun notifyTableUpdated(update: TableDataUpdate) {
        _dataUpdateFlow.tryEmit(update)
    }

}
