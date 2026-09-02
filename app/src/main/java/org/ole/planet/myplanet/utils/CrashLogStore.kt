package org.ole.planet.myplanet.utils

import android.content.Context
import java.io.File

/**
 * Synchronous, dependency-free persistence for crash/ANR reports. Reports are written
 * here before any coroutine or Room machinery runs, because app shutdown can happen
 * before launched coroutines finish persisting rows. Files left behind are swept into ApkLog on the
 * next app start.
 */
object CrashLogStore {
    private const val DIR_NAME = "pending_logs"
    private const val FILE_EXTENSION = ".log"
    private const val MAX_PENDING_FILES = 20

    data class PendingLog(val file: File, val type: String, val time: String, val error: String)

    private fun dir(context: Context): File = File(context.filesDir, DIR_NAME)

    private data class ParsedLogInfo(val time: String, val type: String)

    private fun parseLogFile(file: File): ParsedLogInfo? {
        if (!file.isFile || !file.name.endsWith(FILE_EXTENSION)) return null
        val name = file.name.removeSuffix(FILE_EXTENSION)
        val separator = name.indexOf('_')
        if (separator <= 0) return null
        val time = name.substring(0, separator)
        if (time.toLongOrNull() == null) return null
        val type = name.substring(separator + 1)
        return ParsedLogInfo(time, type)
    }

    private fun isValidLogFile(file: File): Boolean = parseLogFile(file) != null

    fun save(context: Context, type: String, error: String, timeProvider: TimeProvider): File? {
        return try {
            val logDir = dir(context)
            if (!logDir.exists() && !logDir.mkdirs()) return null
            if ((logDir.listFiles()?.count { isValidLogFile(it) } ?: 0) >= MAX_PENDING_FILES) return null
            val file = File(logDir, "${timeProvider.now()}_$type$FILE_EXTENSION")
            file.writeText(error)
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun loadPendingLogs(context: Context): List<PendingLog> {
        val files = dir(context).listFiles() ?: return emptyList()
        return files.mapNotNull { file ->
            val parsed = parseLogFile(file) ?: return@mapNotNull null
            try {
                PendingLog(file, parsed.type, parsed.time, file.readText())
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
