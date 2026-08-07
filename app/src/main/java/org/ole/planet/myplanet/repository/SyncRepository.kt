package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.data.api.ApiInterface

interface SyncRepository {
    fun uploadLoginData(): Flow<SyncUiState>
    fun uploadBulkData(): Flow<SyncUiState>
    suspend fun processShelfParallel(shelfId: String, apiInterface: ApiInterface): Int
    fun syncDashboardKeyId(role: String?, listener: org.ole.planet.myplanet.callback.OnSyncListener)
}

sealed class SyncUiState {
    object Idle : SyncUiState()
    object Loading : SyncUiState()
    data class Success(val message: String?) : SyncUiState()
    data class Error(val message: String?) : SyncUiState()
}
