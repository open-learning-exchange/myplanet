package org.ole.planet.myplanet.utilities

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.service.sync.ServerUrlMapper

object SyncTimeLogger {
    private val processTimes = ConcurrentHashMap<String, Long>()
    private val processItemCounts = ConcurrentHashMap<String, Int>()
    private val apiCallTimes = ConcurrentHashMap<String, MutableList<ApiCallLog>>()
    private val realmOperationTimes = ConcurrentHashMap<String, MutableList<RealmOperationLog>>()
    private val detailedLogs = ConcurrentHashMap<String, MutableList<String>>()
    private var startTime: Long = 0
    private var endTime: Long = 0
    private var isLogging = false
    private val apiCallCounter = AtomicInteger(0)
    private val realmOpCounter = AtomicInteger(0)

    data class ApiCallLog(
        val endpoint: String,
        val duration: Long,
        val timestamp: Long,
        val success: Boolean,
        val itemsReturned: Int = 0
    )

    data class RealmOperationLog(
        val operation: String,
        val model: String,
        val duration: Long,
        val itemCount: Int,
        val timestamp: Long
    )

    fun startLogging() {
        startTime = System.currentTimeMillis()
        isLogging = true
        processTimes.clear()
        processItemCounts.clear()
        apiCallTimes.clear()
        realmOperationTimes.clear()
        detailedLogs.clear()
        apiCallCounter.set(0)
        realmOpCounter.set(0)
        Log.d("SyncPerf", "═══════════════════════════════════════════════════════════════")
        Log.d("SyncPerf", "SYNC STARTED at ${formatTimestamp(startTime)}")
        Log.d("SyncPerf", "═══════════════════════════════════════════════════════════════")
    }

    fun stopLogging(uploadManager: UploadManager? = null) {
        if (!isLogging) return

        endTime = System.currentTimeMillis()
        isLogging = false
        val summary = generateSummary()
        saveSummaryToRealm(summary, uploadManager)

        Log.d("SyncPerf", "═══════════════════════════════════════════════════════════════")
        Log.d("SyncPerf", "SYNC COMPLETED at ${formatTimestamp(endTime)}")
        Log.d("SyncPerf", "TOTAL DURATION: ${formatTime(endTime - startTime)}")
        Log.d("SyncPerf", "═══════════════════════════════════════════════════════════════")
    }

    private fun saveSummaryToRealm(summary: String, uploadManager: UploadManager? = null) {
        val settings = MainApplication.context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        MainApplication.applicationScope.launch(Dispatchers.IO) {
            MainApplication.createLog("sync summary", summary)
            val updateUrl = "${settings.getString("serverURL", "")}"
            val serverUrlMapper = ServerUrlMapper()
            val mapping = serverUrlMapper.processUrl(updateUrl)

            val primaryAvailable = MainApplication.isServerReachable(mapping.primaryUrl)
            val alternativeAvailable =
                mapping.alternativeUrl?.let { MainApplication.isServerReachable(it) } == true

            if (!primaryAvailable && alternativeAvailable) {
                mapping.alternativeUrl?.let { alternativeUrl ->
                    val uri = updateUrl.toUri()
                    val editor = settings.edit()


                    serverUrlMapper.updateUrlPreferences(
                        editor,
                        uri,
                        alternativeUrl,
                        mapping.primaryUrl,
                        settings
                    )
                }
            }
            try {
                uploadManager?.uploadCrashLog()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun startProcess(processName: String) {
        if (!isLogging) return

        val key = "$processName:start"
        processTimes[key] = System.currentTimeMillis()
    }

    fun endProcess(processName: String, itemCount: Int = 0) {
        if (!isLogging) return

        val startKey = "$processName:start"
        val endTime = System.currentTimeMillis()

        if (!processTimes.containsKey(startKey)) {
            return
        }

        val startTime = processTimes[startKey] ?: return
        val duration = endTime - startTime

        processTimes[processName] = duration
        processItemCounts[processName] = itemCount

        val elapsed = endTime - this.startTime
        if (itemCount > 0) {
            Log.d("SyncPerf", "[${formatElapsed(elapsed)}] ✓ $processName completed: ${formatTime(duration)}, $itemCount items")
        } else {
            Log.d("SyncPerf", "[${formatElapsed(elapsed)}] ✓ $processName completed: ${formatTime(duration)}")
        }
    }

    fun logApiCall(endpoint: String, duration: Long, success: Boolean, itemsReturned: Int = 0) {
        if (!isLogging) return

        val timestamp = System.currentTimeMillis()
        val callNum = apiCallCounter.incrementAndGet()
        val elapsed = timestamp - startTime
        val processName = extractProcessName(endpoint)

        val log = ApiCallLog(endpoint, duration, timestamp, success, itemsReturned)
        apiCallTimes.getOrPut(processName) { mutableListOf() }.add(log)

        val statusIcon = if (success) "✓" else "✗"
        val itemInfo = if (itemsReturned > 0) ", $itemsReturned items" else ""
        Log.d("SyncPerf", "[${formatElapsed(elapsed)}] $statusIcon API #$callNum: ${shortenEndpoint(endpoint)} - ${formatTime(duration)}$itemInfo")
    }

    fun logRealmOperation(operation: String, model: String, duration: Long, itemCount: Int) {
        if (!isLogging) return

        val timestamp = System.currentTimeMillis()
        val opNum = realmOpCounter.incrementAndGet()
        val elapsed = timestamp - startTime

        val log = RealmOperationLog(operation, model, duration, itemCount, timestamp)
        realmOperationTimes.getOrPut(model) { mutableListOf() }.add(log)

        Log.d("SyncPerf", "[${formatElapsed(elapsed)}] 💾 DB #$opNum: $operation $model - ${formatTime(duration)}, $itemCount items")
    }

    fun logDetail(context: String, message: String) {
        if (!isLogging) return

        val timestamp = System.currentTimeMillis()
        val elapsed = timestamp - startTime
        detailedLogs.getOrPut(context) { mutableListOf() }.add(message)

        Log.d("SyncPerf", "[${formatElapsed(elapsed)}] ℹ $context: $message")
    }

    private fun extractProcessName(endpoint: String): String {
        // Extract database/collection name from endpoint
        val parts = endpoint.split("/")
        return parts.getOrNull(parts.size - 2) ?: "unknown"
    }

    private fun shortenEndpoint(endpoint: String): String {
        // Shorten long endpoints for readability
        return if (endpoint.length > 60) {
            endpoint.takeLast(60).let { "...$it" }
        } else {
            endpoint
        }
    }

    private fun formatElapsed(elapsed: Long): String {
        val seconds = elapsed / 1000
        val millis = elapsed % 1000
        return String.format(Locale.US, "%3d.%03ds", seconds, millis)
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        return sdf.format(java.util.Date(timestamp))
    }

    private fun generateSummary(): String {
        val totalDuration = endTime - startTime
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalDuration)
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(totalDuration) % 60

        val summaryBuilder = StringBuilder()
        summaryBuilder.append("=== SYNC TIME SUMMARY ===\n")
        summaryBuilder.append("Total sync time: $totalMinutes min $totalSeconds sec (${formatTime(totalDuration)})\n\n")

        // Process times
        summaryBuilder.append("PROCESS BREAKDOWN:\n")
        processTimes.entries
            .filter { !it.key.endsWith(":start") }
            .sortedByDescending { it.value }
            .forEach { (process, duration) ->
                val percentage = (duration.toDouble() / totalDuration.toDouble() * 100).roundToInt()
                val itemCount = processItemCounts[process] ?: 0

                if (itemCount > 0) {
                    val itemsPerSecond = if (duration > 0) (itemCount * 1000.0 / duration).toInt() else 0
                    summaryBuilder.append(String.format(Locale.US, "  %-30s: %10s (%3d%%) - %d items at %d items/sec\n",
                        process, formatTime(duration), percentage, itemCount, itemsPerSecond
                    ))
                } else {
                    summaryBuilder.append(String.format(Locale.US,"  %-30s: %10s (%3d%%)\n",
                        process, formatTime(duration), percentage))
                }
            }

        // API call statistics
        if (apiCallTimes.isNotEmpty()) {
            summaryBuilder.append("\nAPI CALL STATISTICS:\n")
            val totalApiCalls = apiCallTimes.values.sumOf { it.size }
            val totalApiTime = apiCallTimes.values.flatten().sumOf { it.duration }
            val successfulCalls = apiCallTimes.values.flatten().count { it.success }

            summaryBuilder.append(String.format(Locale.US, "  Total API calls: %d (Success: %d, Failed: %d)\n",
                totalApiCalls, successfulCalls, totalApiCalls - successfulCalls))
            summaryBuilder.append(String.format(Locale.US, "  Total API time: %s (%.1f%% of total sync)\n",
                formatTime(totalApiTime), (totalApiTime.toDouble() / totalDuration * 100)))

            apiCallTimes.entries.sortedByDescending { it.value.sumOf { log -> log.duration } }.forEach { (endpoint, logs) ->
                val totalTime = logs.sumOf { it.duration }
                val avgTime = if (logs.isNotEmpty()) totalTime / logs.size else 0
                val totalItems = logs.sumOf { it.itemsReturned }
                summaryBuilder.append(String.format(Locale.US, "    %-25s: %d calls, %10s total, %8s avg, %d items\n",
                    endpoint.take(25), logs.size, formatTime(totalTime), formatTime(avgTime), totalItems))
            }
        }

        // Realm operation statistics
        if (realmOperationTimes.isNotEmpty()) {
            summaryBuilder.append("\nREALM OPERATION STATISTICS:\n")
            val totalRealmOps = realmOperationTimes.values.sumOf { it.size }
            val totalRealmTime = realmOperationTimes.values.flatten().sumOf { it.duration }
            val totalRealmItems = realmOperationTimes.values.flatten().sumOf { it.itemCount }

            summaryBuilder.append(String.format(Locale.US, "  Total Realm operations: %d\n", totalRealmOps))
            summaryBuilder.append(String.format(Locale.US, "  Total Realm time: %s (%.1f%% of total sync)\n",
                formatTime(totalRealmTime), (totalRealmTime.toDouble() / totalDuration * 100)))
            summaryBuilder.append(String.format(Locale.US, "  Total items processed: %d\n", totalRealmItems))

            realmOperationTimes.entries.sortedByDescending { it.value.sumOf { log -> log.duration } }.forEach { (model, logs) ->
                val totalTime = logs.sumOf { it.duration }
                val avgTime = if (logs.isNotEmpty()) totalTime / logs.size else 0
                val totalItems = logs.sumOf { it.itemCount }
                summaryBuilder.append(String.format(Locale.US, "    %-25s: %d ops, %10s total, %8s avg, %d items\n",
                    model.take(25), logs.size, formatTime(totalTime), formatTime(avgTime), totalItems))
            }
        }

        // Performance insights
        summaryBuilder.append("\nPERFORMANCE INSIGHTS:\n")
        val apiPercentage = if (apiCallTimes.isNotEmpty()) {
            (apiCallTimes.values.flatten().sumOf { it.duration }.toDouble() / totalDuration * 100)
        } else 0.0
        val realmPercentage = if (realmOperationTimes.isNotEmpty()) {
            (realmOperationTimes.values.flatten().sumOf { it.duration }.toDouble() / totalDuration * 100)
        } else 0.0

        summaryBuilder.append(String.format(Locale.US, "  Network time: %.1f%%\n", apiPercentage))
        summaryBuilder.append(String.format(Locale.US, "  Database time: %.1f%%\n", realmPercentage))
        summaryBuilder.append(String.format(Locale.US, "  Other processing: %.1f%%\n", 100 - apiPercentage - realmPercentage))

        summaryBuilder.append("=========================")
        return summaryBuilder.toString()
    }

    private fun formatTime(timeMs: Long): String {
        return when {
            timeMs < 1000 -> "${timeMs}ms"
            timeMs < 60000 -> String.format(Locale.US, "%.2fs", timeMs / 1000.0)
            else -> {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(timeMs)
                val seconds = TimeUnit.MILLISECONDS.toSeconds(timeMs) % 60
                val millis = timeMs % 1000
                "${minutes}m ${seconds}.${millis}s"
            }
        }
    }

}
