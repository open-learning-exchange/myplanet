package org.ole.planet.myplanet.utils

import android.content.Context
import java.io.File

/**
 * Synchronous, dependency-free persistence for crash/ANR reports. Reports are written
 * here before any coroutine or Realm machinery runs, because both share the dispatcher
 * and write lock with whatever workload caused the failure — the process may die before
 * an async Realm write commits. Files left behind are swept into RealmApkLog on the
 * next app start.
 */
object CrashLogStore {
    private const val DIR_NAME = "pending_logs"
    private const val FILE_EXTENSION = ".log"
    private const val MAX_PENDING_FILES = 20

    data class PendingLog(val file: File, val type: String, val time: String, val error: String)

    private fun dir(context: Context): File = File(context.filesDir, DIR_NAME)

    fun save(context: Context, type: String, error: String): File? {
        return try {
            val logDir = dir(context)
            if (!logDir.exists() && !logDir.mkdirs()) return null
            if ((logDir.listFiles()?.size ?: 0) >= MAX_PENDING_FILES) return null
            val file = File(logDir, "${System.currentTimeMillis()}_$type$FILE_EXTENSION")
            file.writeText(error)
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun loadPendingLogs(context: Context): List<PendingLog> {
        val files = dir(context).listFiles() ?: return emptyList()
        return files.filter { it.isFile && it.name.endsWith(FILE_EXTENSION) }.mapNotNull { file ->
            try {
                val name = file.name.removeSuffix(FILE_EXTENSION)
                val separator = name.indexOf('_')
                if (separator <= 0) return@mapNotNull null
                val time = name.substring(0, separator)
                if (time.toLongOrNull() == null) return@mapNotNull null
                PendingLog(file, name.substring(separator + 1), time, file.readText())
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
