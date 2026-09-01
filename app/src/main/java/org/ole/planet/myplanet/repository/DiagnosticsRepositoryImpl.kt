package org.ole.planet.myplanet.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import org.ole.planet.myplanet.data.room.dao.ApkLogDao
import org.ole.planet.myplanet.model.ApkLog
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.CrashLogStore
import org.ole.planet.myplanet.utils.VersionUtils

class DiagnosticsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apkLogDao: ApkLogDao,
    private val sharedPrefManager: SharedPrefManager,
    private val userSessionManager: UserSessionManager
) : DiagnosticsRepository {

    override suspend fun getPendingApkLogs(): List<ApkLog> {
        return apkLogDao.getPending()
    }

    override suspend fun markApkLogUploaded(localId: String, rev: String): Boolean {
        return apkLogDao.markUploaded(localId, rev) != 0
    }

    private fun buildApkLog(
        parentCode: String,
        planetCode: String,
        versionName: String?,
        modelId: String?,
        time: String,
        type: String,
        error: String
    ): ApkLog {
        return ApkLog().apply {
            id = "${UUID.randomUUID()}"
            this.parentCode = parentCode
            createdOn = planetCode
            modelId?.let { userId = it }
            this.time = time
            page = ""
            version = versionName
            this.type = type
            if (error.isNotEmpty()) {
                this.error = error
            }
        }
    }

    override suspend fun saveLogToRoom(type: String, error: String, time: String): Boolean {
        return try {
            val model = userSessionManager.getUserModel()
            val log = buildApkLog(
                sharedPrefManager.getParentCode(),
                sharedPrefManager.getPlanetCode(),
                VersionUtils.getVersionName(context),
                model?.id,
                time,
                type,
                error,
            )
            apkLogDao.insert(log)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun saveLogsToRoom(pendingLogs: List<CrashLogStore.PendingLog>): Boolean {
        if (pendingLogs.isEmpty()) return true
        return try {
            val model = userSessionManager.getUserModel()
            val parentCode = sharedPrefManager.getParentCode()
            val planetCode = sharedPrefManager.getPlanetCode()
            val versionName = VersionUtils.getVersionName(context)
            val logsToInsert = pendingLogs.map { pending ->
                buildApkLog(parentCode, planetCode, versionName, model?.id, pending.time, pending.type, pending.error)
            }
            apkLogDao.insertAll(logsToInsert)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
