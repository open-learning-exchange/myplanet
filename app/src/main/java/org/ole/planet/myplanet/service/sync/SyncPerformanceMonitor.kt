package org.ole.planet.myplanet.service.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

data class PerformanceStats(
    val averageDuration: Long,
    val averageThroughput: Double,
    val successRate: Double,
    val totalSyncs: Int,
    val bestStrategy: String?,
    val worstStrategy: String?
)

data class SyncComparison(
    val table: String,
    val standardMetrics: PerformanceStats?,
    val betaMetrics: PerformanceStats?,
    val recommendation: String
)

class SyncPerformanceMonitor(private val context: Context) {
    
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val currentMetrics = ConcurrentHashMap<String, SyncMetrics>()
    private val historicalMetrics = mutableListOf<SyncMetrics>()
    
    private val _realTimeMetrics = MutableStateFlow<Map<String, SyncMetrics>>(emptyMap())
    val realTimeMetrics: StateFlow<Map<String, SyncMetrics>> = _realTimeMetrics.asStateFlow()
    
    private val maxHistoricalRecords = 100
    
    init {
        loadHistoricalMetrics()
    }
    
    fun startSyncTracking(tableName: String, strategy: String, config: SyncConfig): SyncTracker {
        return SyncTracker(tableName, strategy, config, this)
    }
    
    internal fun recordMetrics(metrics: SyncMetrics) {
        currentMetrics[metrics.tableName] = metrics
        historicalMetrics.add(metrics)
        
        // Keep only recent records
        if (historicalMetrics.size > maxHistoricalRecords) {
            historicalMetrics.removeAt(0)
        }
        
        _realTimeMetrics.value = currentMetrics.toMap()
        saveMetricsToPrefs(metrics)
    }
    
    fun getPerformanceStats(tableName: String, strategy: String? = null): PerformanceStats {
        val relevantMetrics = historicalMetrics.filter { 
            it.tableName == tableName && (strategy == null || it.strategy == strategy)
        }
        
        if (relevantMetrics.isEmpty()) {
            return PerformanceStats(0, 0.0, 0.0, 0, null, null)
        }
        
        val successfulSyncs = relevantMetrics.filter { it.success }
        val successRate = successfulSyncs.size.toDouble() / relevantMetrics.size.toDouble()
        
        val avgDuration = successfulSyncs.map { it.duration }.average().toLong()
        val avgThroughput = successfulSyncs.map { it.throughputItemsPerSecond }.average()
        
        val strategiesPerformance = relevantMetrics.groupBy { it.strategy }
            .mapValues { (_, metrics) ->
                metrics.filter { it.success }.map { it.throughputItemsPerSecond }.average()
            }
        
        val bestStrategy = strategiesPerformance.maxByOrNull { it.value }?.key
        val worstStrategy = strategiesPerformance.minByOrNull { it.value }?.key
        
        return PerformanceStats(
            averageDuration = avgDuration,
            averageThroughput = avgThroughput,
            successRate = successRate,
            totalSyncs = relevantMetrics.size,
            bestStrategy = bestStrategy,
            worstStrategy = worstStrategy
        )
    }
    
    fun compareStrategies(tableName: String): SyncComparison {
        val standardStats = getPerformanceStats(tableName, "standard")
        val betaStats = getPerformanceStats(tableName, "beta")
        
        val recommendation = when {
            betaStats.totalSyncs == 0 -> "Use standard sync (no beta data)"
            standardStats.totalSyncs == 0 -> "Use beta sync (no standard data)"
            betaStats.averageThroughput > standardStats.averageThroughput * 1.2 -> 
                "Use beta sync (${((betaStats.averageThroughput / standardStats.averageThroughput - 1) * 100).roundToInt()}% faster)"
            betaStats.successRate < standardStats.successRate * 0.9 -> 
                "Use standard sync (beta less reliable)"
            else -> "Use standard sync (marginal difference)"
        }
        
        return SyncComparison(
            table = tableName,
            standardMetrics = if (standardStats.totalSyncs > 0) standardStats else null,
            betaMetrics = if (betaStats.totalSyncs > 0) betaStats else null,
            recommendation = recommendation
        )
    }
    
    fun getRecommendedStrategy(tableName: String): String {
        val comparison = compareStrategies(tableName)
        return if (comparison.recommendation.contains("beta")) "beta" else "standard"
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
    
    private fun loadHistoricalMetrics() {
        // Load recent metrics from preferences
        // This is a simplified version - in production you might use a database
        val savedMetrics = preferences.all.filterKeys { it.startsWith("sync_metrics_") }
        // Parse and add to historicalMetrics if needed
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
