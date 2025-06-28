package org.ole.planet.myplanet.utilities

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.roundToInt

class CpuMonitoring private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: CpuMonitoring? = null

        fun getInstance(context: Context): CpuMonitoring {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CpuMonitoring(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val monitoringScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var isMonitoring = false
    private var monitoringJob: Job? = null

    // Data structures to store monitoring data
    private val cpuUsageHistory = ConcurrentLinkedQueue<CpuUsageSnapshot>()
    private val memoryUsageHistory = ConcurrentLinkedQueue<MemoryUsageSnapshot>()
    private val threadInfoHistory = ConcurrentLinkedQueue<ThreadInfoSnapshot>()

    // Configuration
    private var monitoringIntervalMs = 5000L // 5 seconds
    private var maxHistorySize = 100 // Keep last 100 snapshots

    data class CpuUsageSnapshot(
        val timestamp: Long,
        val appCpuUsage: Double,
        val systemCpuUsage: Double,
        val processId: Int,
        val threadCount: Int
    )

    data class MemoryUsageSnapshot(
        val timestamp: Long,
        val totalMemoryMb: Double,
        val availableMemoryMb: Double,
        val usedMemoryMb: Double,
        val appMemoryUsageMb: Double,
        val heapSizeMb: Double,
        val heapAllocatedMb: Double,
        val heapFreeMb: Double
    )

    data class ThreadInfoSnapshot(
        val timestamp: Long,
        val threadCount: Int,
        val runningThreads: Int,
        val blockedThreads: Int,
        val waitingThreads: Int,
        val topThreadsByUsage: List<ThreadInfo>
    )

    data class ThreadInfo(
        val name: String,
        val state: String,
        val priority: Int,
        val isDaemon: Boolean
    )

    data class PerformanceReport(
        val reportTimestamp: Long,
        val monitoringDurationMs: Long,
        val avgCpuUsage: Double,
        val maxCpuUsage: Double,
        val avgMemoryUsage: Double,
        val maxMemoryUsage: Double,
        val avgThreadCount: Int,
        val maxThreadCount: Int,
        val recentSnapshots: List<CpuUsageSnapshot>,
        val memorySnapshots: List<MemoryUsageSnapshot>,
        val threadSnapshots: List<ThreadInfoSnapshot>,
        val systemInfo: SystemInfo
    )

    data class SystemInfo(
        val deviceModel: String,
        val androidVersion: String,
        val availableProcessors: Int,
        val totalRam: Long,
        val packageName: String,
        val appVersion: String
    )

    fun startMonitoring(intervalMs: Long = 5000L, maxHistory: Int = 100) {
        if (isMonitoring) return

        monitoringIntervalMs = intervalMs
        maxHistorySize = maxHistory
        isMonitoring = true

        monitoringJob = monitoringScope.launch {
            while (isMonitoring && isActive) {
                try {
                    collectCpuUsage()
                    collectMemoryUsage()
                    collectThreadInfo()

                    // Maintain history size
                    maintainHistorySize()

                    delay(monitoringIntervalMs)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun stopMonitoring() {
        isMonitoring = false
        monitoringJob?.cancel()
    }

    private suspend fun collectCpuUsage() = withContext(Dispatchers.IO) {
        try {
            val processId = Process.myPid()
            val appCpuUsage = getAppCpuUsage(processId)
            val systemCpuUsage = getSystemCpuUsage()
            val threadCount = getThreadCount()

            val snapshot = CpuUsageSnapshot(
                timestamp = System.currentTimeMillis(),
                appCpuUsage = appCpuUsage,
                systemCpuUsage = systemCpuUsage,
                processId = processId,
                threadCount = threadCount
            )

            cpuUsageHistory.offer(snapshot)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun collectMemoryUsage() = withContext(Dispatchers.IO) {
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)

            val runtime = Runtime.getRuntime()
            val totalMemoryMb = memoryInfo.totalMem / (1024.0 * 1024.0)
            val availableMemoryMb = memoryInfo.availMem / (1024.0 * 1024.0)
            val usedMemoryMb = totalMemoryMb - availableMemoryMb

            val heapSizeMb = runtime.totalMemory() / (1024.0 * 1024.0)
            val heapFreeMb = runtime.freeMemory() / (1024.0 * 1024.0)
            val heapAllocatedMb = heapSizeMb - heapFreeMb

            val appMemoryUsageMb = getAppMemoryUsage()

            val snapshot = MemoryUsageSnapshot(
                timestamp = System.currentTimeMillis(),
                totalMemoryMb = totalMemoryMb,
                availableMemoryMb = availableMemoryMb,
                usedMemoryMb = usedMemoryMb,
                appMemoryUsageMb = appMemoryUsageMb,
                heapSizeMb = heapSizeMb,
                heapAllocatedMb = heapAllocatedMb,
                heapFreeMb = heapFreeMb
            )

            memoryUsageHistory.offer(snapshot)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun collectThreadInfo() = withContext(Dispatchers.IO) {
        try {
            val allThreads = Thread.getAllStackTraces().keys
            val threadCount = allThreads.size

            var runningThreads = 0
            var blockedThreads = 0
            var waitingThreads = 0

            val threadInfos = mutableListOf<ThreadInfo>()

            allThreads.forEach { thread ->
                when (thread.state) {
                    Thread.State.RUNNABLE -> runningThreads++
                    Thread.State.BLOCKED -> blockedThreads++
                    Thread.State.WAITING, Thread.State.TIMED_WAITING -> waitingThreads++
                    else -> {}
                }

                threadInfos.add(
                    ThreadInfo(
                        name = thread.name,
                        state = thread.state.name,
                        priority = thread.priority,
                        isDaemon = thread.isDaemon
                    )
                )
            }

            // Sort by priority and take top threads
            val topThreads = threadInfos.sortedByDescending { it.priority }.take(10)

            val snapshot = ThreadInfoSnapshot(
                timestamp = System.currentTimeMillis(),
                threadCount = threadCount,
                runningThreads = runningThreads,
                blockedThreads = blockedThreads,
                waitingThreads = waitingThreads,
                topThreadsByUsage = topThreads
            )

            threadInfoHistory.offer(snapshot)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getAppCpuUsage(processId: Int): Double {
        return try {
            val statFile = "/proc/$processId/stat"
            val reader = BufferedReader(FileReader(statFile))
            val statLine = reader.readLine()
            reader.close()

            val statFields = statLine.split(" ")
            if (statFields.size >= 17) {
                val utime = statFields[13].toLongOrNull() ?: 0L
                val stime = statFields[14].toLongOrNull() ?: 0L
                val cutime = statFields[15].toLongOrNull() ?: 0L
                val cstime = statFields[16].toLongOrNull() ?: 0L

                val totalTime = utime + stime + cutime + cstime
                val clockTicks = 100.0 // Typical clock ticks per second

                // This is a simplified calculation - for more accurate results,
                // you'd need to calculate the difference over time
                (totalTime / clockTicks).coerceAtMost(100.0)
            } else {
                0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }

    private fun getSystemCpuUsage(): Double {
        return try {
            val reader = BufferedReader(FileReader("/proc/stat"))
            val cpuLine = reader.readLine()
            reader.close()

            val cpuTimes = cpuLine.split("\\s+".toRegex()).drop(1).map { it.toLongOrNull() ?: 0L }
            if (cpuTimes.size >= 4) {
                val idle = cpuTimes[3]
                val total = cpuTimes.sum()
                val usage = ((total - idle).toDouble() / total.toDouble()) * 100.0
                usage.coerceIn(0.0, 100.0)
            } else {
                0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }

    private fun getThreadCount(): Int {
        return try {
            val statusFile = "/proc/${Process.myPid()}/status"
            val reader = BufferedReader(FileReader(statusFile))
            var line: String?
            var threadCount = 0

            while (reader.readLine().also { line = it } != null) {
                if (line?.startsWith("Threads:") == true) {
                    threadCount = line?.split("\\s+".toRegex())?.get(1)?.toIntOrNull() ?: 0
                    break
                }
            }
            reader.close()
            threadCount
        } catch (e: Exception) {
            Thread.activeCount()
        }
    }

    private fun getAppMemoryUsage(): Double {
        return try {
            val memoryInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memoryInfo)
            memoryInfo.totalPss / 1024.0 // Convert KB to MB
        } catch (e: Exception) {
            0.0
        }
    }

    private fun maintainHistorySize() {
        while (cpuUsageHistory.size > maxHistorySize) {
            cpuUsageHistory.poll()
        }
        while (memoryUsageHistory.size > maxHistorySize) {
            memoryUsageHistory.poll()
        }
        while (threadInfoHistory.size > maxHistorySize) {
            threadInfoHistory.poll()
        }
    }

    fun generatePerformanceReport(): PerformanceReport {
        val currentTime = System.currentTimeMillis()
        val cpuSnapshots = cpuUsageHistory.toList()
        val memorySnapshots = memoryUsageHistory.toList()
        val threadSnapshots = threadInfoHistory.toList()

        val monitoringDuration = if (cpuSnapshots.isNotEmpty()) {
            currentTime - cpuSnapshots.first().timestamp
        } else {
            0L
        }

        val avgCpuUsage = cpuSnapshots.map { it.appCpuUsage }.average().takeIf { !it.isNaN() } ?: 0.0
        val maxCpuUsage = cpuSnapshots.maxOfOrNull { it.appCpuUsage } ?: 0.0

        val avgMemoryUsage = memorySnapshots.map { it.appMemoryUsageMb }.average().takeIf { !it.isNaN() } ?: 0.0
        val maxMemoryUsage = memorySnapshots.maxOfOrNull { it.appMemoryUsageMb } ?: 0.0

        val avgThreadCount = threadSnapshots.map { it.threadCount }.average().takeIf { !it.isNaN() }?.roundToInt() ?: 0
        val maxThreadCount = threadSnapshots.maxOfOrNull { it.threadCount } ?: 0

        return PerformanceReport(
            reportTimestamp = currentTime,
            monitoringDurationMs = monitoringDuration,
            avgCpuUsage = avgCpuUsage,
            maxCpuUsage = maxCpuUsage,
            avgMemoryUsage = avgMemoryUsage,
            maxMemoryUsage = maxMemoryUsage,
            avgThreadCount = avgThreadCount,
            maxThreadCount = maxThreadCount,
            recentSnapshots = cpuSnapshots.takeLast(20), // Last 20 snapshots
            memorySnapshots = memorySnapshots.takeLast(20),
            threadSnapshots = threadSnapshots.takeLast(20),
            systemInfo = getSystemInfo()
        )
    }

    private fun getSystemInfo(): SystemInfo {
        return SystemInfo(
            deviceModel = android.os.Build.MODEL,
            androidVersion = android.os.Build.VERSION.RELEASE,
            availableProcessors = Runtime.getRuntime().availableProcessors(),
            totalRam = getTotalRam(),
            packageName = context.packageName,
            appVersion = getAppVersion()
        )
    }

    private fun getTotalRam(): Long {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            memoryInfo.totalMem
        } catch (e: Exception) {
            0L
        }
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    fun getFormattedReport(): String {
        val report = generatePerformanceReport()

        return buildString {
            appendLine("=== PERFORMANCE MONITORING REPORT ===")
            appendLine("Report Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(report.reportTimestamp))}")
            appendLine("Monitoring Duration: ${report.monitoringDurationMs / 1000}s")
            appendLine()

            appendLine("=== SYSTEM INFO ===")
            appendLine("Device: ${report.systemInfo.deviceModel}")
            appendLine("Android Version: ${report.systemInfo.androidVersion}")
            appendLine("CPU Cores: ${report.systemInfo.availableProcessors}")
            appendLine("Total RAM: ${report.systemInfo.totalRam / (1024 * 1024)} MB")
            appendLine("App Package: ${report.systemInfo.packageName}")
            appendLine("App Version: ${report.systemInfo.appVersion}")
            appendLine()

            appendLine("=== CPU USAGE SUMMARY ===")
            appendLine("Average CPU Usage: ${String.format("%.2f", report.avgCpuUsage)}%")
            appendLine("Peak CPU Usage: ${String.format("%.2f", report.maxCpuUsage)}%")
            appendLine()

            appendLine("=== MEMORY USAGE SUMMARY ===")
            appendLine("Average Memory Usage: ${String.format("%.2f", report.avgMemoryUsage)} MB")
            appendLine("Peak Memory Usage: ${String.format("%.2f", report.maxMemoryUsage)} MB")
            appendLine()

            appendLine("=== THREAD SUMMARY ===")
            appendLine("Average Thread Count: ${report.avgThreadCount}")
            appendLine("Peak Thread Count: ${report.maxThreadCount}")
            appendLine()

            if (report.recentSnapshots.isNotEmpty()) {
                appendLine("=== RECENT CPU SNAPSHOTS ===")
                report.recentSnapshots.takeLast(5).forEach { snapshot ->
                    val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(snapshot.timestamp))
                    appendLine("$time - CPU: ${String.format("%.2f", snapshot.appCpuUsage)}%, Threads: ${snapshot.threadCount}")
                }
                appendLine()
            }

            if (report.threadSnapshots.isNotEmpty()) {
                appendLine("=== THREAD DETAILS (LATEST) ===")
                val latestThreadInfo = report.threadSnapshots.lastOrNull()
                latestThreadInfo?.let { info ->
                    appendLine("Total Threads: ${info.threadCount}")
                    appendLine("Running: ${info.runningThreads}, Blocked: ${info.blockedThreads}, Waiting: ${info.waitingThreads}")
                    appendLine("Top Threads:")
                    info.topThreadsByUsage.take(5).forEach { thread ->
                        appendLine("  - ${thread.name} (${thread.state}, Priority: ${thread.priority})")
                    }
                }
            }
        }
    }

    fun clearHistory() {
        cpuUsageHistory.clear()
        memoryUsageHistory.clear()
        threadInfoHistory.clear()
    }
}