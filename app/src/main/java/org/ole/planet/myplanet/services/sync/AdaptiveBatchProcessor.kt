package org.ole.planet.myplanet.services.sync

/**
 * Adjusts sync batch sizes to network conditions (AIMD): slow or failed
 * batches halve the size so weak links get small pages; fast batches grow
 * it back toward [maxSize]. Not thread-safe — use one instance per loop.
 */
class AdaptiveBatchProcessor(
    initialSize: Int,
    private val minSize: Int = MIN_BATCH_SIZE,
    maxSize: Int = initialSize,
    private val slowThresholdMs: Long = SLOW_THRESHOLD_MS,
    private val fastThresholdMs: Long = FAST_THRESHOLD_MS,
) {
    private val maxSize: Int = maxOf(maxSize, minSize)

    var currentSize: Int = initialSize.coerceIn(minSize, this.maxSize)
        private set

    fun recordSuccess(durationMs: Long) {
        currentSize = when {
            durationMs >= slowThresholdMs -> (currentSize / 2).coerceAtLeast(minSize)
            durationMs <= fastThresholdMs -> (currentSize + currentSize / 4 + 1).coerceAtMost(maxSize)
            else -> currentSize
        }
    }

    fun recordFailure() {
        currentSize = (currentSize / 2).coerceAtLeast(minSize)
    }

    companion object {
        private const val MIN_BATCH_SIZE = 10
        private const val SLOW_THRESHOLD_MS = 8_000L
        private const val FAST_THRESHOLD_MS = 2_000L
    }
}
