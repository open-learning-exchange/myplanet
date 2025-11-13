package org.ole.planet.myplanet.service.sync

import io.realm.Realm
import kotlinx.coroutines.flow.Flow

data class SyncConfig(
    val batchSize: Int = 50,
    val concurrencyLevel: Int = 3,
    val retryAttempts: Int = 3,
    val timeoutMs: Long = 30000,
    val enableOptimizations: Boolean = true,
    val fallbackToStandard: Boolean = true
)

data class SyncResult(
    val table: String,
    val processedItems: Int,
    val success: Boolean,
    val errorMessage: String? = null,
    val duration: Long,
    val strategy: String
)

import org.ole.planet.myplanet.datamanager.DatabaseService

interface SyncStrategy {
    suspend fun syncTable(
        table: String,
        realm: Realm,
        config: SyncConfig,
        databaseService: DatabaseService
    ): Flow<SyncResult>

    fun getStrategyName(): String
    fun isSupported(table: String): Boolean
}

sealed class SyncMode {
    object Standard : SyncMode()
    object Fast : SyncMode()
    object Optimized : SyncMode()
}
