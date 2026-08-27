package org.ole.planet.myplanet.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import org.ole.planet.myplanet.data.room.dao.ApkLogDao
import org.ole.planet.myplanet.model.ApkLog
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.CrashLogStore
import org.ole.planet.myplanet.utils.VersionUtils

class DiagnosticsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apkLogDao: ApkLogDao,
    private val userRepository: UserRepository
) : DiagnosticsRepository {

    override suspend fun getPendingApkLogs(): List<ApkLog> {
        return apkLogDao.getPending()
    }

    override suspend fun markApkLogUploaded(localId: String, rev: String): Boolean {
        return apkLogDao.markUploaded(localId, rev) != 0
    }

    private fun buildApkLog(
        model: UserEntity?,
        time: String,
        type: String,
        error: String
    ): ApkLog {
        return ApkLog().apply {
            id = "${UUID.randomUUID()}"
            parentCode = model?.parentCode
            createdOn = model?.planetCode
            model?.id?.let { userId = it }
            this.time = time
            page = ""
            version = VersionUtils.getVersionName(context)
            this.type = type
            if (error.isNotEmpty()) {
                this.error = error
            }
        }
    }

    override suspend fun saveLogToRoom(type: String, error: String, time: String): Boolean {
        return try {
            val model = userRepository.getUserModel()
            val log = buildApkLog(model, time, type, error)
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
            val versionName = VersionUtils.getVersionName(context)

            val logsToInsert = pendingLogs.map { pending ->
                ApkLog().apply {
                    id = "${UUID.randomUUID()}"
                    this.parentCode = model?.parentCode
                    this.createdOn = model?.planetCode
                    model?.let { userId = it.id }
                    this.time = pending.time
                    page = ""
                    version = versionName
                    this.type = pending.type
                    if (pending.error.isNotEmpty()) {
                        this.error = pending.error
                    }
                }
            }
            apkLogDao.insertAll(logsToInsert)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
