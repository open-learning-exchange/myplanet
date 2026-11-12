package org.ole.planet.myplanet.services

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadServiceRemainingCountTest {

    @Test
    fun `both sets empty returns zero`() {
        val result = DownloadService.countRemaining(
            priority = emptySet(),
            pending = emptySet(),
            processed = emptySet()
        )
        assertEquals(0, result)
    }

    @Test
    fun `disjoint sets with none processed returns sum of sizes`() {
        val priority = setOf("a", "b")
        val pending = setOf("c", "d")
        val result = DownloadService.countRemaining(
            priority = priority,
            pending = pending,
            processed = emptySet()
        )
        assertEquals(4, result)
    }

    @Test
    fun `url in both priority and pending is counted once`() {
        val priority = setOf("a", "b")
        val pending = setOf("b", "c")
        val result = DownloadService.countRemaining(
            priority = priority,
            pending = pending,
            processed = emptySet()
        )
        assertEquals(3, result)
    }

    @Test
    fun `processed urls are excluded from both sides`() {
        val priority = setOf("a", "b")
        val pending = setOf("b", "c")
        val processed = setOf("b")
        val result = DownloadService.countRemaining(
            priority = priority,
            pending = pending,
            processed = processed
        )
        assertEquals(2, result)
    }

    @Test
    fun `every url processed returns zero`() {
        val priority = setOf("a", "b")
        val pending = setOf("b", "c")
        val processed = setOf("a", "b", "c")
        val result = DownloadService.countRemaining(
            priority = priority,
            pending = pending,
            processed = processed
        )
        assertEquals(0, result)
    }
}
