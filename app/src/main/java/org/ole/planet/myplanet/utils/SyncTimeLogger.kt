package org.ole.planet.myplanet.utils

import android.util.Log
import androidx.core.net.toUri
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.repository.DiagnosticsRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UploadManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper

@Singleton
class SyncTimeLogger @Inject constructor(
    private val timeProvider: TimeProvider,
    @ApplicationScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val sharedPrefManager: SharedPrefManager,
    private val serverUrlMapper: ServerUrlMapper,
    private val diagnosticsRepository: DiagnosticsRepository,
    private val serverReachabilityProvider: ServerReachabilityProvider
) {

    private val processTimes = ConcurrentHashMap<String, Long>()
    private val processItemCounts = ConcurrentHashMap<String, Int>()
    private val apiCallTimes = ConcurrentHashMap<String, MutableList<ApiCallLog>>()
    private val dbOperationTimes = ConcurrentHashMap<String, MutableList<DbOperationLog>>()
    private val detailedLogs = ConcurrentHashMap<String, MutableList<String>>()
    private var startTime: Long = 0
    private var endTime: Long = 0
    private var isLogging = false
    private val apiCallCounter = AtomicInteger(0)
    private val dbOpCounter = AtomicInteger(0)

    data class ApiCallLog(
        val endpoint: String,
        val duration: Long,
        val timestamp: Long,
        val success: Boolean,
        val itemsReturned: Int = 0
    )

    data class DbOperationLog(
        val operation: String,
        val model: String,
        val duration: Long,
        val itemCount: Int,
        val timestamp: Long
    )

    fun startLogging() {
        startTime = timeProvider.now()
        isLogging = true
        processTimes.clear()
        processItemCounts.clear()
        apiCallTimes.clear()
        dbOperationTimes.clear()
        detailedLogs.clear()
        apiCallCounter.set(0)
        dbOpCounter.set(0)
        if (Log.isLoggable("SyncPerf", Log.DEBUG)) {
            Log.d("SyncPerf", "═══════════════════════════════════════════════════════════════")
            Log.d("SyncPerf", "SYNC STARTED at ${formatTimestamp(startTime)}")
            Log.d("SyncPerf", "═══════════════════════════════════════════════════════════════")
        }
    }

    fun stopLogging(uploadManager: UploadManager? = null) {
        if (!isLogging) return

        endTime = timeProvider.now()
        isLogging = false
        val summary = generateSummary()
        saveSummaryToRoom(summary, uploadManager)

        if (Log.isLoggable("SyncPerf", Log.DEBUG)) {
            Log.d("SyncPerf", "═══════════════════════════════════════════════════════════════")
            Log.d("SyncPerf", "SYNC COMPLETED at ${formatTimestamp(endTime)}")
            Log.d("SyncPerf", "TOTAL DURATION: ${formatTime(endTime - startTime)}")
            Log.d("SyncPerf", "═══════════════════════════════════════════════════════════════")
        }
    }


    private fun saveSummaryToRoom(summary: String, uploadManager: UploadManager? = null) {
        appScope.launch(dispatcherProvider.io) {
            diagnosticsRepository.saveLogToRoom("sync summary", summary, "${timeProvider.now()}")
            val updateUrl = sharedPrefManager.getServerUrl()
            val mapping = serverUrlMapper.processUrl(updateUrl)

            val primaryAvailable = serverReachabilityProvider.isServerReachable(mapping.primaryUrl)
            val alternativeUrl = mapping.alternativeUrl

            if (!primaryAvailable && alternativeUrl != null && serverReachabilityProvider.isServerReachable(alternativeUrl)) {
                val uri = updateUrl.toUri()
                val prefs = sharedPrefManager.rawPreferences
                val editor = prefs.edit()

                serverUrlMapper.updateUrlPreferences(
                    editor,
                    uri,
                    alternativeUrl,
                    mapping.primaryUrl,
                    prefs
                )
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
        processTimes[key] = timeProvider.now()
    }

    fun endProcess(processName: String, itemCount: Int = 0) {
        if (!isLogging) return

        val startKey = "$processName:start"
        val endTime = timeProvider.now()

        if (!processTimes.containsKey(startKey)) {
            return
        }

        val startTime = processTimes[startKey] ?: return
        val duration = endTime - startTime

        processTimes[processName] = duration
        processItemCounts[processName] = itemCount

        if (Log.isLoggable("SyncPerf", Log.DEBUG)) {
            val elapsed = endTime - this.startTime
            if (itemCount > 0) {
                Log.d("SyncPerf", "[${formatElapsed(elapsed)}] ✓ $processName completed: ${formatTime(duration)}, $itemCount items")
            } else {
                Log.d("SyncPerf", "[${formatElapsed(elapsed)}] ✓ $processName completed: ${formatTime(duration)}")
            }
        }
    }

    fun logApiCall(endpoint: String, duration: Long, success: Boolean, itemsReturned: Int = 0) {
        if (!isLogging) return

        val timestamp = timeProvider.now()
        val callNum = apiCallCounter.incrementAndGet()
        val processName = extractProcessName(endpoint)

        val log = ApiCallLog(endpoint, duration, timestamp, success, itemsReturned)
        apiCallTimes.getOrPut(processName) { mutableListOf() }.add(log)

        if (Log.isLoggable("SyncPerf", Log.DEBUG)) {
            val elapsed = timestamp - startTime
            val statusIcon = if (success) "✓" else "✗"
            val itemInfo = if (itemsReturned > 0) ", $itemsReturned items" else ""
            Log.d("SyncPerf", "[${formatElapsed(elapsed)}] $statusIcon API #$callNum: ${shortenEndpoint(endpoint)} - ${formatTime(duration)}$itemInfo")
        }
    }

    fun logDbOperation(operation: String, model: String, duration: Long, itemCount: Int) {
        if (!isLogging) return

        val timestamp = timeProvider.now()
        val opNum = dbOpCounter.incrementAndGet()

        val log = DbOperationLog(operation, model, duration, itemCount, timestamp)
        dbOperationTimes.getOrPut(model) { mutableListOf() }.add(log)

        if (Log.isLoggable("SyncPerf", Log.DEBUG)) {
            val elapsed = timestamp - startTime
            Log.d("SyncPerf", "[${formatElapsed(elapsed)}] 💾 DB #$opNum: $operation $model - ${formatTime(duration)}, $itemCount items")
        }
    }

    fun logDetail(context: String, message: String) {
        if (!isLogging) return

        detailedLogs.getOrPut(context) { mutableListOf() }.add(message)

        if (Log.isLoggable("SyncPerf", Log.DEBUG)) {
            val timestamp = timeProvider.now()
            val elapsed = timestamp - startTime
            Log.d("SyncPerf", "[${formatElapsed(elapsed)}] ℹ $context: $message")
        }
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

    private val timestampFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.US).withZone(ZoneId.systemDefault())

    private fun formatTimestamp(timestamp: Long): String {
        return timestampFormat.format(Instant.ofEpochMilli(timestamp))
    }

    internal fun generateSummary(): String {
        val totalDuration = endTime - startTime
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalDuration)
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(totalDuration) % 60

        val summaryBuilder = StringBuilder()
        summaryBuilder.append("=== SYNC TIME SUMMARY ===\n")
        summaryBuilder.append("Total sync time: $totalMinutes min $totalSeconds sec (${formatTime(totalDuration)})\n\n")

        val allApiCallLogs = apiCallTimes.values.flatten()
        val allDbOpLogs = dbOperationTimes.values.flatten()

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
            val totalApiTime = allApiCallLogs.sumOf { it.duration }
            val successfulCalls = allApiCallLogs.count { it.success }

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
        if (dbOperationTimes.isNotEmpty()) {
            summaryBuilder.append("\nDB OPERATION STATISTICS:\n")
            val totalDbOps = dbOperationTimes.values.sumOf { it.size }
            val totalDbTime = allDbOpLogs.sumOf { it.duration }
            val totalDbItems = allDbOpLogs.sumOf { it.itemCount }

            summaryBuilder.append(String.format(Locale.US, "  Total Db operations: %d\n", totalDbOps))
            summaryBuilder.append(String.format(Locale.US, "  Total Db time: %s (%.1f%% of total sync)\n",
                formatTime(totalDbTime), (totalDbTime.toDouble() / totalDuration * 100)))
            summaryBuilder.append(String.format(Locale.US, "  Total items processed: %d\n", totalDbItems))

            dbOperationTimes.entries.sortedByDescending { it.value.sumOf { log -> log.duration } }.forEach { (model, logs) ->
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
            (allApiCallLogs.sumOf { it.duration }.toDouble() / totalDuration * 100)
        } else 0.0
        val dbPercentage = if (dbOperationTimes.isNotEmpty()) {
            (allDbOpLogs.sumOf { it.duration }.toDouble() / totalDuration * 100)
        } else 0.0

        summaryBuilder.append(String.format(Locale.US, "  Network time: %.1f%%\n", apiPercentage))
        summaryBuilder.append(String.format(Locale.US, "  Database time: %.1f%%\n", dbPercentage))
        summaryBuilder.append(String.format(Locale.US, "  Other processing: %.1f%%\n", 100 - apiPercentage - dbPercentage))

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


    companion object {
        internal fun extractProcessName(endpoint: String): String {
            val segments = endpoint.split("/")

            val lastValidSegment = segments.lastOrNull {
                it.isNotEmpty() && !it.startsWith("?")
            } ?: return "Unknown"

            val withoutQuery = lastValidSegment.substringBefore("?")
            if (withoutQuery.isEmpty()) return "Unknown"

            return withoutQuery.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
            }
        }
    }
}
