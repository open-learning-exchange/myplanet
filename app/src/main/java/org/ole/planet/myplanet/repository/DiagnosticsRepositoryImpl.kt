package org.ole.planet.myplanet.repository

import java.util.UUID
import javax.inject.Inject
import org.ole.planet.myplanet.BuildConfig
import org.ole.planet.myplanet.data.room.dao.ApkLogDao
import org.ole.planet.myplanet.model.ApkLog
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.CrashLogStore

class DiagnosticsRepositoryImpl @Inject constructor(
    private val apkLogDao: ApkLogDao,
    private val userRepository: UserRepository,
    private val sharedPrefManager: SharedPrefManager
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

    private fun resolveParentCode(model: UserEntity?): String =
        model?.parentCode?.takeIf { it.isNotBlank() } ?: sharedPrefManager.getParentCode()

    private fun resolvePlanetCode(model: UserEntity?): String =
        model?.planetCode?.takeIf { it.isNotBlank() } ?: sharedPrefManager.getPlanetCode()

    override suspend fun saveLogToRoom(type: String, error: String, time: String): Boolean {
        return try {
            val model = userRepository.getUserModel()
            val log = buildApkLog(
                resolveParentCode(model),
                resolvePlanetCode(model),
                BuildConfig.VERSION_NAME,
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
            val model = userRepository.getUserModel()
            val versionName = BuildConfig.VERSION_NAME
            val parentCode = resolveParentCode(model)
            val planetCode = resolvePlanetCode(model)

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
