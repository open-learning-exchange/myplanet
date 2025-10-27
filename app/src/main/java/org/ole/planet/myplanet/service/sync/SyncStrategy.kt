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

interface SyncStrategy {
    suspend fun syncTable(
        table: String,
        realm: Realm,
        config: SyncConfig
    ): Flow<Unit>

    fun getStrategyName(): String
    fun isSupported(table: String): Boolean
}

sealed class SyncMode {
    object Standard : SyncMode()
    object Fast : SyncMode()
    object Optimized : SyncMode()
}
