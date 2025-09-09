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
    ): Flow<SyncResult> = flow {
        val startTime = System.currentTimeMillis()
        
        try {
            // Use the existing TransactionSyncManager for standard sync
            TransactionSyncManager.syncDb(realm, table)
            
            val endTime = System.currentTimeMillis()
            emit(
                SyncResult(
                    table = table,
                    processedItems = -1, // TransactionSyncManager doesn't return count
                    success = true,
                    duration = endTime - startTime,
                    strategy = getStrategyName()
                )
            )
        } catch (e: Exception) {
            val endTime = System.currentTimeMillis()
            emit(
                SyncResult(
                    table = table,
                    processedItems = 0,
                    success = false,
                    errorMessage = e.message,
                    duration = endTime - startTime,
                    strategy = getStrategyName()
                )
            )
        }
    }
    
    override suspend fun syncTableWithProgress(
        table: String,
        realm: Realm,
        config: SyncConfig
    ): Flow<SyncProgress> = flow {
        // Standard sync doesn't provide detailed progress
        emit(
            SyncProgress(
                table = table,
                processedItems = 0,
                totalItems = -1,
                currentBatch = 1,
                totalBatches = 1
            )
        )
    }
    
    override fun getStrategyName(): String = "standard"
    
    override fun isSupported(table: String): Boolean = true
}
