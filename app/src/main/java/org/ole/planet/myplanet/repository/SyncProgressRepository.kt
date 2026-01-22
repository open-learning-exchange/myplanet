package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ole.planet.myplanet.di.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncProgressRepository @Inject constructor(
    @AppPreferences private val preferences: SharedPreferences
) {

    private val _syncProgress = MutableStateFlow(SyncProgress())
    val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()

    init {
        loadLastSyncTimes()
    }

    private fun loadLastSyncTimes() {
        val currentMap = mutableMapOf<String, TableStatus>()
        preferences.all.forEach { (key, value) ->
            if (key.startsWith("last_sync_")) {
                val table = key.removePrefix("last_sync_")
                if (value is Long) {
                    currentMap[table] = TableStatus(lastSyncTime = value)
                }
            }
        }
        if (currentMap.isNotEmpty()) {
            _syncProgress.value = _syncProgress.value.copy(perTableStatus = currentMap)
        }
    }

    fun updateProgress(
        currentTable: String? = null,
        progress: Int? = null,
        max: Int? = null,
        state: SyncState? = null
    ) {
        _syncProgress.value = _syncProgress.value.copy(
            currentTable = currentTable ?: _syncProgress.value.currentTable,
            progress = progress ?: _syncProgress.value.progress,
            max = max ?: _syncProgress.value.max,
            state = state ?: _syncProgress.value.state
        )
    }

    fun updateTableProgress(table: String, progress: Int, max: Int) {
        val currentMap = _syncProgress.value.perTableStatus.toMutableMap()
        val currentTableStatus = currentMap[table] ?: TableStatus(
            lastSyncTime = preferences.getLong("last_sync_$table", 0)
        )
        currentMap[table] = currentTableStatus.copy(
            progress = progress,
            max = max,
            status = SyncState.SYNCING
        )
        _syncProgress.value = _syncProgress.value.copy(
            currentTable = table,
            progress = progress,
            max = max,
            perTableStatus = currentMap
        )
    }

    fun setTableState(table: String, state: SyncState, lastSyncTime: Long? = null) {
        val currentMap = _syncProgress.value.perTableStatus.toMutableMap()
        val currentTableStatus = currentMap[table] ?: TableStatus(
            lastSyncTime = preferences.getLong("last_sync_$table", 0)
        )

        if (lastSyncTime != null) {
             preferences.edit { putLong("last_sync_$table", lastSyncTime) }
        }

        currentMap[table] = currentTableStatus.copy(
            status = state,
            lastSyncTime = lastSyncTime ?: currentTableStatus.lastSyncTime
        )
        _syncProgress.value = _syncProgress.value.copy(
            currentTable = table,
            perTableStatus = currentMap
        )
    }

    fun reset() {
        // Reset progress but keep history
        val currentMap = _syncProgress.value.perTableStatus.toMutableMap()
        currentMap.keys.forEach { table ->
            currentMap[table] = currentMap[table]!!.copy(
                progress = 0,
                max = 0,
                status = SyncState.IDLE
            )
        }
        _syncProgress.value = SyncProgress(perTableStatus = currentMap)
    }
}

data class SyncProgress(
    val currentTable: String = "",
    val progress: Int = 0,
    val max: Int = 0,
    val state: SyncState = SyncState.IDLE,
    val perTableStatus: Map<String, TableStatus> = emptyMap()
)

data class TableStatus(
    val status: SyncState = SyncState.IDLE,
    val progress: Int = 0,
    val max: Int = 0,
    val lastSyncTime: Long = 0
)

enum class SyncState {
    IDLE,
    SYNCING,
    SUCCESS,
    FAILED
}
