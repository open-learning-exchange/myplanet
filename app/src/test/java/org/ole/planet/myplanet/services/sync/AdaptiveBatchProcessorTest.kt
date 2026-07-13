package org.ole.planet.myplanet.services.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveBatchProcessorTest {
    @Test
    fun `starts at initial size`() {
        val processor = AdaptiveBatchProcessor(initialSize = 100)
        assertEquals(100, processor.currentSize)
    }

    @Test
    fun `initial size is clamped to min`() {
        val processor = AdaptiveBatchProcessor(initialSize = 5, minSize = 10)
        assertEquals(10, processor.currentSize)
    }

    @Test
    fun `failure halves the batch size`() {
        val processor = AdaptiveBatchProcessor(initialSize = 100)
        processor.recordFailure()
        assertEquals(50, processor.currentSize)
    }

    @Test
    fun `repeated failures never drop below min size`() {
        val processor = AdaptiveBatchProcessor(initialSize = 100, minSize = 10)
        repeat(10) { processor.recordFailure() }
        assertEquals(10, processor.currentSize)
    }

    @Test
    fun `slow response halves the batch size`() {
        val processor = AdaptiveBatchProcessor(initialSize = 100, slowThresholdMs = 8_000)
        processor.recordSuccess(durationMs = 9_000)
        assertEquals(50, processor.currentSize)
    }

    @Test
    fun `fast response grows the batch size`() {
        val processor = AdaptiveBatchProcessor(initialSize = 100, maxSize = 200, fastThresholdMs = 2_000)
        processor.recordSuccess(durationMs = 500)
        assertEquals(126, processor.currentSize)
    }

    @Test
    fun `growth never exceeds max size`() {
        val processor = AdaptiveBatchProcessor(initialSize = 100)
        repeat(10) { processor.recordSuccess(durationMs = 100) }
        assertEquals(100, processor.currentSize)
    }

    @Test
    fun `moderate response keeps size unchanged`() {
        val processor = AdaptiveBatchProcessor(initialSize = 100, slowThresholdMs = 8_000, fastThresholdMs = 2_000)
        processor.recordSuccess(durationMs = 5_000)
        assertEquals(100, processor.currentSize)
    }

    @Test
    fun `recovers toward max after a slow spell`() {
        val processor = AdaptiveBatchProcessor(initialSize = 100)
        processor.recordFailure()
        processor.recordFailure()
        assertEquals(25, processor.currentSize)
        repeat(20) { processor.recordSuccess(durationMs = 100) }
        assertEquals(100, processor.currentSize)
    }
}
