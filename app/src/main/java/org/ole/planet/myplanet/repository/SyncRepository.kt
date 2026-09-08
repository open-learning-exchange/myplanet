package org.ole.planet.myplanet.repository

import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    fun uploadLoginData(): Flow<SyncUiState>
    fun uploadBulkData(): Flow<SyncUiState>
    suspend fun processShelfParallel(shelfId: String): Int
    suspend fun syncDashboardKeyId(role: String?): SyncUiState
    fun getCachedShelvesWithData(): List<String>
    fun cacheShelvesWithData(shelves: List<String>)
}

sealed class SyncUiState {
    object Idle : SyncUiState()
    object Loading : SyncUiState()
    data class Success(val message: String?) : SyncUiState()
    data class Error(val message: String?) : SyncUiState()
}
