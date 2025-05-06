package org.ole.planet.myplanet.utilities

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * A utility class that helps log execution times of sync processes and calculates
 * statistics such as total duration and percentage of time each process takes.
 */
class SyncTimeLogger private constructor() {
    private val processTimes = ConcurrentHashMap<String, Long>()
    private var startTime: Long = 0
    private var endTime: Long = 0
    private var isLogging = false

    /**
     * Starts the logging session and records the start time
     */
    fun startLogging() {
        startTime = System.currentTimeMillis()
        isLogging = true
        processTimes.clear()
        Log.d(TAG, "SyncTimeLogger started")
    }

    /**
     * Stops the logging session and records the end time
     */
    fun stopLogging() {
        if (!isLogging) return

        endTime = System.currentTimeMillis()
        isLogging = false
        logSummary()
    }

    /**
     * Records the start time of a process
     * @param processName Name of the process to track
     */
    fun startProcess(processName: String) {
        if (!isLogging) return

        val key = "$processName:start"
        processTimes[key] = System.currentTimeMillis()
        Log.d(TAG, "Process started: $processName")
    }

    /**
     * Records the end time of a process and logs its duration
     * @param processName Name of the process that was tracked
     */
    fun endProcess(processName: String) {
        if (!isLogging) return

        val startKey = "$processName:start"
        val endTime = System.currentTimeMillis()

        if (!processTimes.containsKey(startKey)) {
            Log.w(TAG, "Process $processName ended without being started")
            return
        }

        val startTime = processTimes[startKey] ?: return
        val duration = endTime - startTime

        processTimes[processName] = duration
        Log.d(TAG, "Process completed: $processName in ${formatTime(duration)}")
    }

    /**
     * Gets the duration of a specific process in milliseconds
     * @param processName Name of the process
     * @return Duration in milliseconds or 0 if process wasn't tracked
     */
    fun getProcessDuration(processName: String): Long {
        return processTimes[processName] ?: 0
    }

    /**
     * Logs a detailed summary of all tracked processes with their durations and percentages
     */
    private fun logSummary() {
        val totalDuration = endTime - startTime
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalDuration)
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(totalDuration) % 60

        Log.i(TAG, "=== SYNC TIME SUMMARY ===")
        Log.i(TAG, "Total sync time: $totalMinutes min $totalSeconds sec (${formatTime(totalDuration)})")
        Log.i(TAG, "Individual process times:")

        // Filter out the start time entries and sort by duration (longest first)
        processTimes.entries
            .filter { !it.key.endsWith(":start") }
            .sortedByDescending { it.value }
            .forEach { (process, duration) ->
                val percentage = (duration.toDouble() / totalDuration.toDouble() * 100).roundToInt()
                Log.i(TAG, String.format("%-30s: %10s (%3d%%)",
                    process, formatTime(duration), percentage))
            }

        Log.i(TAG, "=========================")
    }

    /**
     * Formats time in milliseconds to a human-readable string
     * @param timeMs Time in milliseconds
     * @return Formatted time string
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