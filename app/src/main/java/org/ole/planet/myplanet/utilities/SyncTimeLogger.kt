package org.ole.planet.myplanet.utilities

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.service.UploadManager
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class SyncTimeLogger private constructor() {
    private val processTimes = ConcurrentHashMap<String, Long>()
    private val processItemCounts = ConcurrentHashMap<String, Int>()
    private var startTime: Long = 0
    private var endTime: Long = 0
    private var isLogging = false
    private val handler = Handler(Looper.getMainLooper())

    fun startLogging() {
        startTime = System.currentTimeMillis()
        isLogging = true
        processTimes.clear()
        processItemCounts.clear()
    }

    fun stopLogging() {
        if (!isLogging) return

        endTime = System.currentTimeMillis()
        isLogging = false
        val summary = generateSummary()
        saveSummaryToRealm(summary)
    }

    private fun saveSummaryToRealm(summary: String) {
        val settings = MainApplication.context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        handler.post {
            MainApplication.createLog("sync summary", summary)
            val updateUrl = "${settings.getString("serverURL", "")}"
            val serverUrlMapper = ServerUrlMapper()
            val mapping = serverUrlMapper.processUrl(updateUrl)

            CoroutineScope(Dispatchers.IO).launch {
                val primaryAvailable = MainApplication.isServerReachable(mapping.primaryUrl)
                val alternativeAvailable =
                    mapping.alternativeUrl?.let { MainApplication.isServerReachable(it) } == true

                if (!primaryAvailable && alternativeAvailable) {
                    mapping.alternativeUrl.let { alternativeUrl ->
                        val uri = updateUrl.toUri()
                        val editor = settings.edit()

                        serverUrlMapper.updateUrlPreferences(editor, uri, alternativeUrl, mapping.primaryUrl, settings)
                    }
                }

                withContext(Dispatchers.Main) {
                    uploadCrashLogs()
                }
            }
        }
    }

    private fun uploadCrashLogs() {
        MainApplication.applicationScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    UploadManager.instance?.uploadCrashLog()
                }
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
    }

    private fun generateSummary(): String {
        val totalDuration = endTime - startTime
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalDuration)
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(totalDuration) % 60

        val summaryBuilder = StringBuilder()
        summaryBuilder.append("=== SYNC TIME SUMMARY ===\n")
        summaryBuilder.append("Total sync time: $totalMinutes min $totalSeconds sec (${formatTime(totalDuration)})\n")
        summaryBuilder.append("Individual process times:\n")

        processTimes.entries
            .filter { !it.key.endsWith(":start") }
            .sortedByDescending { it.value }
            .forEach { (process, duration) ->
                val percentage = (duration.toDouble() / totalDuration.toDouble() * 100).roundToInt()
                val itemCount = processItemCounts[process] ?: 0

                if (itemCount > 0) {
                    val itemsPerSecond = if (duration > 0) (itemCount * 1000.0 / duration).toInt() else 0
                    summaryBuilder.append(String.format(Locale.US, "%-30s: %10s (%3d%%) - %d items at %d items/sec\n",
                        process, formatTime(duration), percentage, itemCount, itemsPerSecond
                    ))
                } else {
                    summaryBuilder.append(String.format(Locale.US,"%-30s: %10s (%3d%%)\n",
                        process, formatTime(duration), percentage))
                }
            }

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
