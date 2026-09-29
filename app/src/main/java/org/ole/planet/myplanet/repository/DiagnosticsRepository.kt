package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.ApkLog
import org.ole.planet.myplanet.utils.CrashLogStore

data class ApkLogUpload(val id: String, val rev: String)

interface DiagnosticsRepository {
    suspend fun getPendingApkLogs(): List<ApkLog>
    suspend fun markApkLogsUploaded(updates: List<ApkLogUpload>): Set<String>
    suspend fun saveLogToRoom(type: String, error: String, time: String): Boolean
    suspend fun saveLogsToRoom(pendingLogs: List<CrashLogStore.PendingLog>): Boolean
}
