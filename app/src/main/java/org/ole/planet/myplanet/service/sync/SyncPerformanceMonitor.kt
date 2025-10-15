package org.ole.planet.myplanet.service.sync

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
