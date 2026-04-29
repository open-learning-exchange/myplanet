package org.ole.planet.myplanet.services.sync

data class GroupSyncResult(
    val itemsSynced: Int,
    val success: Boolean,
    val results: Map<String, Int> = emptyMap()
)

interface TableGroupSyncHandler {
    suspend fun syncFull(): GroupSyncResult
    suspend fun syncFast(syncTables: List<String>?): GroupSyncResult
}
