package org.ole.planet.myplanet.services.sync

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import org.ole.planet.myplanet.model.TableDataUpdate

@Singleton
class RealtimeSyncManager @Inject constructor() {
    
    private val _dataUpdateFlow = MutableSharedFlow<TableDataUpdate>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val dataUpdateFlow: SharedFlow<TableDataUpdate> = _dataUpdateFlow.asSharedFlow()

    fun updatesFor(table: String): Flow<TableDataUpdate> {
        return _dataUpdateFlow.filter { it.table == table }
    }

    fun notifyTableUpdated(update: TableDataUpdate) {
        _dataUpdateFlow.tryEmit(update)
    }

}
