package org.ole.planet.myplanet.utilities

import android.content.Context
import android.util.Log
import org.ole.planet.myplanet.MainApplication.Companion.context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Enhanced SyncTimeLogger that tracks timing for individual resources and processes,
 * with detailed reporting capabilities.
 */
class SyncTimeLogger private constructor() {
    private val processTimes = ConcurrentHashMap<String, Long>()
    private val processItemCounts = ConcurrentHashMap<String, Int>()
    private val resourceTimes = ConcurrentHashMap<String, Long>()
    private val resourceStartTimes = ConcurrentHashMap<String, Long>()
    private var startTime: Long = 0
    private var endTime: Long = 0
    private var isLogging = false
    private var logToFile = false
    private var logFile: File? = null
    private var fileWriter: FileWriter? = null

    fun startLogging(logToFile: Boolean = false) {
        startTime = System.currentTimeMillis()
        isLogging = true
        processTimes.clear()
        processItemCounts.clear()
        resourceTimes.clear()
        resourceStartTimes.clear()

        this.logToFile = logToFile
        if (logToFile) {
            initLogFile()
        }

        Log.d(TAG, "SyncTimeLogger started")
        logLine("SyncTimeLogger started at ${formatDateTime(startTime)}")
    }

    private fun initLogFile() {
        try {
            val dateTime = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            logFile = File(context.getExternalFilesDir(null), "sync_log_$dateTime.txt")
            fileWriter = FileWriter(logFile, true)
            logLine("=== SYNC LOG STARTED AT ${formatDateTime(startTime)} ===")
            Log.d(TAG, "Logging to file: ${logFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize log file", e)
            logToFile = false
        }
    }

    fun stopLogging() {
        if (!isLogging) return

        endTime = System.currentTimeMillis()
        isLogging = false

        // Log summary information
        logSummary()

        // Log detailed resource times
        logResourceTimes()

        if (logToFile) {
            try {
                fileWriter?.flush()
                fileWriter?.close()
                Log.d(TAG, "Sync log saved to ${logFile?.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Error closing log file", e)
            }
        }
    }

    fun startProcess(processName: String) {
        if (!isLogging) return

        val key = "$processName:start"
        processTimes[key] = System.currentTimeMillis()
        Log.d(TAG, "Process started: $processName")
        logLine("Process started: $processName at ${formatDateTime(System.currentTimeMillis())}")
    }

    fun endProcess(processName: String, itemCount: Int = 0) {
        if (!isLogging) return

        val startKey = "$processName:start"
        val endTime = System.currentTimeMillis()

        if (!processTimes.containsKey(startKey)) {
            Log.w(TAG, "Process $processName ended without being started")
            logLine("WARNING: Process $processName ended without being started")
            return
        }

        val startTime = processTimes[startKey] ?: return
        val duration = endTime - startTime

        processTimes[processName] = duration
        processItemCounts[processName] = itemCount

        val logMessage = if (itemCount > 0) {
            val itemsPerSecond = if (duration > 0) (itemCount * 1000.0 / duration).toInt() else 0
            "Process completed: $processName in ${formatTime(duration)} " +
                    "($itemCount items, $itemsPerSecond items/sec)"
        } else {
            "Process completed: $processName in ${formatTime(duration)}"
        }

        Log.d(TAG, logMessage)
        logLine(logMessage)
    }

    /**
     * Start tracking the sync time for an individual resource
     */
    fun startResourceSync(resourceId: String) {
        if (!isLogging) return

        resourceStartTimes[resourceId] = System.currentTimeMillis()
        logLine("Resource started: $resourceId at ${formatDateTime(System.currentTimeMillis())}")
    }

    /**
     * End tracking the sync time for an individual resource
     */
    fun endResourceSync(resourceId: String) {
        if (!isLogging) return

        val startTime = resourceStartTimes.remove(resourceId) ?: return
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime

        resourceTimes[resourceId] = duration

        // Only log times over 100ms to reduce log volume
        if (duration > 100) {
            logLine("Resource completed: $resourceId in ${formatTime(duration)}")
        }
    }

    /**
     * Log a summary of all process times
     */
    private fun logSummary() {
        val totalDuration = endTime - startTime
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalDuration)
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(totalDuration) % 60

        val summaryHeader = "=== SYNC TIME SUMMARY ==="
        Log.i(TAG, summaryHeader)
        logLine(summaryHeader)

        val totalTimeMsg = "Total sync time: $totalMinutes min $totalSeconds sec (${formatTime(totalDuration)})"
        Log.i(TAG, totalTimeMsg)
        logLine(totalTimeMsg)

        val processHeader = "Individual process times:"
        Log.i(TAG, processHeader)
        logLine(processHeader)

        // Filter out the start time entries and sort by duration (longest first)
        processTimes.entries
            .filter { !it.key.endsWith(":start") }
            .sortedByDescending { it.value }
            .forEach { (process, duration) ->
                val percentage = (duration.toDouble() / totalDuration.toDouble() * 100).roundToInt()
                val itemCount = processItemCounts[process] ?: 0

                val logMessage = if (itemCount > 0) {
                    val itemsPerSecond = if (duration > 0) (itemCount * 1000.0 / duration).toInt() else 0
                    String.format("%-30s: %10s (%3d%%) - %d items at %d items/sec",
                        process, formatTime(duration), percentage, itemCount, itemsPerSecond)
                } else {
                    String.format("%-30s: %10s (%3d%%)",
                        process, formatTime(duration), percentage)
                }

                Log.i(TAG, logMessage)
                logLine(logMessage)
            }

        val summaryFooter = "========================="
        Log.i(TAG, summaryFooter)
        logLine(summaryFooter)
    }

    /**
     * Log detailed information about resource sync times
     */
    private fun logResourceTimes() {
        if (resourceTimes.isEmpty()) return

        val header = "=== INDIVIDUAL RESOURCE SYNC TIMES ==="
        Log.i(TAG, header)
        logLine(header)

        val countMsg = "Total resources tracked: ${resourceTimes.size}"
        Log.i(TAG, countMsg)
        logLine(countMsg)

        // Group resources by their type
        val groupedResources = resourceTimes.entries.groupBy {
                entry -> entry.key.split("_").firstOrNull() ?: "unknown"
        }

        // For each resource type
        groupedResources.forEach { (type, resources) ->
            val typeHeader = "Resource type: $type (${resources.size} items)"
            Log.i(TAG, typeHeader)
            logLine(typeHeader)

            // Get stats for this type
            val totalTimeForType = resources.sumOf { it.value }
            val avgTimeForType = if (resources.isNotEmpty()) totalTimeForType / resources.size else 0
            val maxTimeForType = resources.maxOfOrNull { it.value } ?: 0
            val minTimeForType = resources.minOfOrNull { it.value } ?: 0

            val statsMsg = String.format(
                "Stats for %s: avg=%s, max=%s, min=%s, total=%s",
                type,
                formatTime(avgTimeForType),
                formatTime(maxTimeForType),
                formatTime(minTimeForType),
                formatTime(totalTimeForType)
            )
            Log.i(TAG, statsMsg)
            logLine(statsMsg)

            // Sort and output the slowest resources for this type
            resources
                .sortedByDescending { it.value }
                .take(10) // Show top 10 slowest resources per type
                .forEach { (resourceId, duration) ->
                    if (duration > 100) { // Filter out very fast resources
                        val resourceMsg = String.format("  %-50s: %s",
                            resourceId.take(50), formatTime(duration))
                        Log.i(TAG, resourceMsg)
                        logLine(resourceMsg)
                    }
                }

            logLine("") // Empty line between types
        }

        // Overall statistics
        val totalTime = resourceTimes.values.sum()
        val avgTime = if (resourceTimes.isNotEmpty()) totalTime / resourceTimes.size else 0
        val maxTime = resourceTimes.values.maxOrNull() ?: 0
        val minTime = resourceTimes.values.minOrNull() ?: 0

        val overallStats = "Overall resource sync statistics:"
        Log.i(TAG, overallStats)
        logLine(overallStats)

        val avgMsg = "Average time per resource: ${formatTime(avgTime)}"
        Log.i(TAG, avgMsg)
        logLine(avgMsg)

        val maxMsg = "Longest resource sync: ${formatTime(maxTime)}"
        Log.i(TAG, maxMsg)
        logLine(maxMsg)

        val minMsg = "Shortest resource sync: ${formatTime(minTime)}"
        Log.i(TAG, minMsg)
        logLine(minMsg)

        val footer = "=================================="
        Log.i(TAG, footer)
        logLine(footer)

        // Write the full resource logs to a separate file if needed
        if (logToFile) {
            writeDetailedResourceLogs()
        }
    }

    /**
     * Write full detailed resource logs to a separate file
     */
    private fun writeDetailedResourceLogs() {
        try {
            val dateTime = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val detailedLogFile = File(
                context.getExternalFilesDir(null),
                "sync_resources_detailed_$dateTime.csv"
            )

            FileWriter(detailedLogFile).use { writer ->
                // Write CSV header
                writer.write("Resource ID,Duration (ms),Duration (formatted)\n")

                // Write all resource times, sorted by duration (slowest first)
                resourceTimes.entries
                    .sortedByDescending { it.value }
                    .forEach { (resourceId, duration) ->
                        writer.write("\"$resourceId\",${duration},\"${formatTime(duration)}\"\n")
                    }
            }

            Log.d(TAG, "Detailed resource sync times saved to ${detailedLogFile.absolutePath}")
            logLine("Detailed resource sync times saved to ${detailedLogFile.absolutePath}")

        } catch (e: Exception) {
            Log.e(TAG, "Error writing detailed resource logs", e)
        }
    }

    /**
     * Format time in milliseconds to a human-readable string
     */
    private fun formatTime(timeMs: Long): String {
        return when {
            timeMs < 1000 -> "${timeMs}ms"
            timeMs < 60000 -> String.format("%.2fs", timeMs / 1000.0)
            else -> {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(timeMs)
                val seconds = TimeUnit.MILLISECONDS.toSeconds(timeMs) % 60
                val millis = timeMs % 1000
                "${minutes}m ${seconds}.${millis}s"
            }
        }
    }

    /**
     * Format timestamp to a human-readable date and time
     */
    private fun formatDateTime(timeMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        return sdf.format(Date(timeMs))
    }

    /**
     * Write a line to the log file if logging to file is enabled
     */
    private fun logLine(line: String) {
        if (!logToFile || fileWriter == null) return

        try {
            fileWriter?.write("${formatDateTime(System.currentTimeMillis())} - $line\n")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to log file", e)
        }
    }

    companion object {
        private const val TAG = "SyncTimeLogger"
        private var instance: SyncTimeLogger? = null

        @JvmStatic
        fun getInstance(): SyncTimeLogger {
            if (instance == null) {
                synchronized(SyncTimeLogger::class.java) {
                    if (instance == null) {
                        instance = SyncTimeLogger()
                    }
                }
            }
            return instance!!
        }
    }
}