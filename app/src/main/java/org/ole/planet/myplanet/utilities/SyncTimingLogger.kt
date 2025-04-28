package org.ole.planet.myplanet.utilities

import android.util.Log

object SyncTimingLogger {
    private val timings = mutableMapOf<String, Long>()
    private var syncStartTime = 0L

    fun startSync() {
        syncStartTime = System.currentTimeMillis()
        timings.clear()
        logOperation("sync_started")
    }

    fun logOperation(operation: String) {
        val currentTime = System.currentTimeMillis()
        val elapsedFromStart = currentTime - syncStartTime
        timings[operation] = currentTime

        val previousOperation = timings.keys.toList().dropLast(1).lastOrNull()
        val timeSincePrevious = if (previousOperation != null) {
            currentTime - (timings[previousOperation] ?: currentTime)
        } else {
            0
        }

        Log.d("SYNC_TIMING", "$operation - Time from start: ${elapsedFromStart}ms, Since previous: ${timeSincePrevious}ms")
    }

    fun endSync() {
        val totalTime = System.currentTimeMillis() - syncStartTime
        Log.d("SYNC_TIMING", "SYNC COMPLETED - Total time: ${totalTime}ms")

        // Print summary of all operations
        Log.d("SYNC_TIMING", "===== SYNC TIMING SUMMARY =====")
        var previousTime = syncStartTime
        timings.forEach { (operation, time) ->
            val duration = time - previousTime
            Log.d("SYNC_TIMING", "$operation: ${duration}ms")
            previousTime = time
        }
        Log.d("SYNC_TIMING", "=============================")
    }
}