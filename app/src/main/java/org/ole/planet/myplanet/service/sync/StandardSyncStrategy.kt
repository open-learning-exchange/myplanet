package org.ole.planet.myplanet.service.sync

import io.realm.Realm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.ole.planet.myplanet.service.TransactionSyncManager

class StandardSyncStrategy : SyncStrategy {
    
    override suspend fun syncTable(
        table: String,
        realm: Realm,
        config: SyncConfig
    ): Flow<Unit> = flow {
        TransactionSyncManager.syncDb(realm, table)
        emit(Unit)
    }
    
    override fun getStrategyName(): String = "standard"

    override fun isSupported(table: String): Boolean = true
}
