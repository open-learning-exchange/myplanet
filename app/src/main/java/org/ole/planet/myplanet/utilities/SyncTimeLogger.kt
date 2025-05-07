package org.ole.planet.myplanet.utilities

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * A utility class that helps log execution times of sync processes and calculates
 * statistics such as total duration and percentage of time each process takes.
 */
/**
 * Utility class to log sync times and calculate performance metrics
 */
class SyncTimeLogger private constructor() {
    private val processTimes = ConcurrentHashMap<String, Long>()
    private val processItemCounts = ConcurrentHashMap<String, Int>()
    private var startTime: Long = 0
    private var endTime: Long = 0
    private var isLogging = false

    fun startLogging() {
        startTime = System.currentTimeMillis()
        isLogging = true
        processTimes.clear()
        processItemCounts.clear()
        Log.d(TAG, "SyncTimeLogger started")
    }

    fun stopLogging() {
        if (!isLogging) return

        endTime = System.currentTimeMillis()
        isLogging = false
        logSummary()
    }

    fun startProcess(processName: String) {
        if (!isLogging) return

        val key = "$processName:start"
        processTimes[key] = System.currentTimeMillis()
        Log.d(TAG, "Process started: $processName")
    }

    fun endProcess(processName: String, itemCount: Int = 0) {
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
        processItemCounts[processName] = itemCount

        if (itemCount > 0) {
            val itemsPerSecond = if (duration > 0) (itemCount * 1000.0 / duration).toInt() else 0
            Log.d(TAG, "Process completed: $processName in ${formatTime(duration)} " +
                    "($itemCount items, $itemsPerSecond items/sec)")
        } else {
            Log.d(TAG, "Process completed: $processName in ${formatTime(duration)}")
        }
    }

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
                val itemCount = processItemCounts[process] ?: 0

                if (itemCount > 0) {
                    val itemsPerSecond = if (duration > 0) (itemCount * 1000.0 / duration).toInt() else 0
                    Log.i(TAG, String.format("%-30s: %10s (%3d%%) - %d items at %d items/sec",
                        process, formatTime(duration), percentage, itemCount, itemsPerSecond))
                } else {
                    Log.i(TAG, String.format("%-30s: %10s (%3d%%)",
                        process, formatTime(duration), percentage))
                }
            }

        Log.i(TAG, "=========================")
    }

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