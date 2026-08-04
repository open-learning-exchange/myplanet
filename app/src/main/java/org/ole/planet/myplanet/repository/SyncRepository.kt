package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.utils.Constants

interface SyncRepository {
    fun uploadLoginData(): Flow<SyncUiState>
    fun uploadBulkData(): Flow<SyncUiState>
    suspend fun processShelfParallel(shelfId: String, apiInterface: ApiInterface): Int
    suspend fun processShelfDataOptimizedSync(shelfId: String?, shelfData: Constants.ShelfData, shelfDoc: JsonObject?, apiInterface: ApiInterface): Int
}

sealed class SyncUiState {
    object Idle : SyncUiState()
    object Loading : SyncUiState()
    data class Success(val message: String?) : SyncUiState()
    data class Error(val message: String?) : SyncUiState()
}
