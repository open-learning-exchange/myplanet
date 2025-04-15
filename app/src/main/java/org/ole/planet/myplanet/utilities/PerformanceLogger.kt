package org.ole.planet.myplanet.utilities

import android.util.Log

object PerformanceLogger {
    private const val TAG = "PerformanceTracker"
    private val timeMarkers = mutableMapOf<String, Long>()
    private var startTime: Long = 0

    fun start() {
        startTime = System.currentTimeMillis()
        timeMarkers.clear()
        Log.d(TAG, "Performance tracking started")
    }

    fun markEvent(eventName: String) {
        val currentTime = System.currentTimeMillis()
        val elapsedFromStart = currentTime - startTime
        val previousEventTime = timeMarkers.values.maxOrNull() ?: startTime
        val elapsedFromPrevious = currentTime - previousEventTime

        timeMarkers[eventName] = currentTime

        Log.d(TAG, "Event: $eventName | Time from start: ${elapsedFromStart}ms | Time from previous: ${elapsedFromPrevious}ms")
    }

    fun end() {
        val totalTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "Performance tracking ended. Total time: ${totalTime}ms")

        // Log summary
        Log.d(TAG, "===== PERFORMANCE SUMMARY =====")
        var previousTime = startTime
        timeMarkers.entries.sortedBy { it.value }.forEach { (event, time) ->
            val elapsedFromStart = time - startTime
            val elapsedFromPrevious = time - previousTime
            Log.d(TAG, "$event: ${elapsedFromStart}ms (+" +
                    "${elapsedFromPrevious}ms from previous)")
            previousTime = time
        }
        Log.d(TAG, "==============================")
    }
}