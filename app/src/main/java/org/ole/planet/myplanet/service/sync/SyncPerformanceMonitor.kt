package org.ole.planet.myplanet.service.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME

data class SyncMetrics(
    val tableName: String,
    val strategy: String,
    val startTime: Long,
    val endTime: Long,
    val duration: Long,
    val itemsProcessed: Int,
    val batchSize: Int,
    val concurrencyLevel: Int,
    val success: Boolean,
    val errorMessage: String? = null,
    val networkSpeed: NetworkSpeed,
    val memoryUsageMB: Long,
    val throughputItemsPerSecond: Double
) {
    companion object {
        fun create(
            tableName: String,
            strategy: String,
            startTime: Long,
            endTime: Long,
            itemsProcessed: Int,
            config: SyncConfig,
            success: Boolean,
            errorMessage: String? = null,
            networkSpeed: NetworkSpeed,
            memoryUsageMB: Long
        ): SyncMetrics {
            val duration = endTime - startTime
            val throughput = if (duration > 0) {
                (itemsProcessed.toDouble() / duration) * 1000.0 // items per second
            } else 0.0
            
            return SyncMetrics(
                tableName = tableName,
                strategy = strategy,
                startTime = startTime,
                endTime = endTime,
                duration = duration,
                itemsProcessed = itemsProcessed,
                batchSize = config.batchSize,
                concurrencyLevel = config.concurrencyLevel,
                success = success,
                errorMessage = errorMessage,
                networkSpeed = networkSpeed,
                memoryUsageMB = memoryUsageMB,
                throughputItemsPerSecond = throughput
            )
        }
    }
}

class SyncPerformanceMonitor(private val context: Context) {

    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun startSyncTracking(tableName: String, strategy: String, config: SyncConfig): SyncTracker {
        return SyncTracker(tableName, strategy, config, this)
    }

    internal fun recordMetrics(metrics: SyncMetrics) {
        saveMetricsToPrefs(metrics)
    }

    private fun saveMetricsToPrefs(metrics: SyncMetrics) {
        val key = "sync_metrics_${metrics.tableName}_${metrics.strategy}"
        preferences.edit {
            putLong("${key}_duration", metrics.duration)
            putFloat("${key}_throughput", metrics.throughputItemsPerSecond.toFloat())
            putBoolean("${key}_success", metrics.success)
            putLong("${key}_timestamp", metrics.endTime)
        }
    }
    
}

class SyncTracker(
    private val tableName: String,
    private val strategy: String,
    private val config: SyncConfig,
    private val monitor: SyncPerformanceMonitor
) {
    private val startTime = System.currentTimeMillis()
    private var itemsProcessed = 0
    
    fun incrementProcessedItems(count: Int = 1) {
        itemsProcessed += count
    }
    
    fun complete(success: Boolean, errorMessage: String? = null) {
        val endTime = System.currentTimeMillis()
        val metrics = SyncMetrics.create(
            tableName = tableName,
            strategy = strategy,
            startTime = startTime,
            endTime = endTime,
            itemsProcessed = itemsProcessed,
            config = config,
            success = success,
            errorMessage = errorMessage,
            networkSpeed = NetworkSpeed.UNKNOWN, // Could be detected
            memoryUsageMB = getMemoryUsage()
        )
        
        monitor.recordMetrics(metrics)
    }
    
    private fun getMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }
}
