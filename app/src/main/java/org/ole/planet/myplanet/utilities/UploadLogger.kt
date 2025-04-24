package org.ole.planet.myplanet.utilities

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility class to track upload process times and generate reports
 */
class UploadLogger {
    private val processTimings = ConcurrentHashMap<String, ProcessTiming>()
    private var totalStartTime: Long = 0
    private var totalEndTime: Long = 0

    data class ProcessTiming(
        val processName: String,
        var startTime: Long = 0,
        var endTime: Long = 0,
        var duration: Long = 0,
        var completed: Boolean = false,
        var message: String = ""
    ) {
        fun getDurationFormatted(): String {
            return "${duration}ms (${duration / 1000.0}s)"
        }
    }

    /**
     * Start tracking the entire upload process
     */
    fun startTotalProcess() {
        totalStartTime = System.currentTimeMillis()
        Log.d(TAG, "====== STARTING COMPLETE UPLOAD PROCESS at ${formatTime(totalStartTime)} ======")
    }

    /**
     * Mark the total process as complete and generate a summary report
     */
    fun endTotalProcess() {
        totalEndTime = System.currentTimeMillis()
        val totalDuration = totalEndTime - totalStartTime

        Log.d(TAG, "====== COMPLETE UPLOAD PROCESS FINISHED ======")
        Log.d(TAG, "Total duration: ${totalDuration}ms (${totalDuration/1000.0}s)")
        generateSummaryReport()
    }

    /**
     * Start tracking a specific upload process
     */
    fun startProcess(processName: String) {
        val startTime = System.currentTimeMillis()
        val timing = ProcessTiming(processName, startTime = startTime)
        processTimings[processName] = timing
        Log.d(TAG, "Starting $processName upload at ${formatTime(startTime)}")
    }

    /**
     * Mark a specific upload process as complete
     */
    fun endProcess(processName: String, message: String = "") {
        val endTime = System.currentTimeMillis()
        val timing = processTimings[processName] ?: return

        timing.endTime = endTime
        timing.duration = endTime - timing.startTime
        timing.completed = true
        timing.message = message

        Log.d(TAG, "$processName upload completed in ${timing.getDurationFormatted()}" +
                if (message.isNotEmpty()) ": $message" else "")
    }

    /**
     * Generate a detailed summary report of all upload processes
     */
    private fun generateSummaryReport() {
        val totalDuration = totalEndTime - totalStartTime

        // Sort processes by duration (descending)
        val sortedProcesses = processTimings.values.sortedByDescending { it.duration }

        val sb = StringBuilder()
        sb.appendLine("====== UPLOAD PROCESS SUMMARY REPORT ======")
        sb.appendLine("Total upload duration: ${totalDuration}ms (${totalDuration/1000.0}s)")
        sb.appendLine("Number of processes: ${processTimings.size}")
        sb.appendLine("\nProcess times (sorted by duration):")

        sortedProcesses.forEachIndexed { index, timing ->
            val percentage = if (totalDuration > 0) (timing.duration * 100.0 / totalDuration).toInt() else 0
            sb.appendLine("${index + 1}. ${timing.processName}: ${timing.getDurationFormatted()} ($percentage%)" +
                    if (!timing.completed) " [INCOMPLETE]" else "")
        }

        sb.appendLine("\nProcess times (in execution order):")
        processTimings.values.forEachIndexed { index, timing ->
            sb.appendLine("${index + 1}. ${timing.processName}: ${timing.getDurationFormatted()}")
        }

        Log.d(TAG, sb.toString())
    }

    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    companion object {
        private const val TAG = "UploadProcess"

        @Volatile
        private var instance: UploadLogger? = null

        fun getInstance(): UploadLogger {
            return instance ?: synchronized(this) {
                instance ?: UploadLogger().also { instance = it }
            }
        }
    }
}