package org.ole.planet.myplanet.services.sync

import io.realm.Realm
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.ole.planet.myplanet.di.RealmDispatcher

class StandardSyncStrategy @Inject constructor(
    private val transactionSyncManager: TransactionSyncManager,
    @RealmDispatcher private val ioDispatcher: CoroutineDispatcher
) : SyncStrategy {
    
    override suspend fun syncTable(
        table: String,
        config: SyncConfig
    ): Flow<SyncResult> = flow {
        val startTime = System.currentTimeMillis()
        
        try {
            // Use the existing TransactionSyncManager for standard sync
            val processedItems = transactionSyncManager.syncDb(table)

            val endTime = System.currentTimeMillis()
            emit(
                SyncResult(
                    table = table,
                    processedItems = processedItems,
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
    }.flowOn(ioDispatcher)
    
    override fun getStrategyName(): String = "standard"

    override fun isSupported(table: String): Boolean = true
}
