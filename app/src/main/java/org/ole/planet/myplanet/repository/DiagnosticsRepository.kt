package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.ApkLog
import org.ole.planet.myplanet.utils.CrashLogStore

interface DiagnosticsRepository {
    suspend fun getPendingApkLogs(): List<ApkLog>
    suspend fun markApkLogUploaded(localId: String, rev: String): Boolean
    suspend fun saveLogToRoom(type: String, error: String, time: String): Boolean
    suspend fun saveLogsToRoom(pendingLogs: List<CrashLogStore.PendingLog>): Boolean
}
