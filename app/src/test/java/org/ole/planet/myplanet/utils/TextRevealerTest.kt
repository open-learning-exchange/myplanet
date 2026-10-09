package org.ole.planet.myplanet.utils

import kotlin.math.ceil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextRevealerTest {

    @Test
    fun testChunkSize() {
        assertEquals(1, TextRevealer.chunkSize(0))
        assertEquals(1, TextRevealer.chunkSize(1))
        assertEquals(1, TextRevealer.chunkSize(199))
        assertEquals(2, TextRevealer.chunkSize(400))
        assertEquals(8, TextRevealer.chunkSize(1600))
        assertEquals(8, TextRevealer.chunkSize(100_000))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testRevealLongString() = runTest {
        val input = "a".repeat(3000)
        val updates = mutableListOf<String>()
        TextRevealer.reveal(input) { updates.add(it) }

        val expectedCount = ceil(3000 / 8.0).toInt()
        assertEquals(expectedCount, updates.size)
        assertTrue(updates.size <= 375)

        var prevLength = 0
        for (update in updates) {
            assertTrue(input.startsWith(update))
            assertTrue(update.length > prevLength)
            prevLength = update.length
        }
        assertEquals(input, updates.last())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testRevealShortString() = runTest {
        val input = "abcde"
        val updates = mutableListOf<String>()
        TextRevealer.reveal(input) { updates.add(it) }

        assertEquals(listOf("a", "ab", "abc", "abcd", "abcde"), updates)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testRevealEmptyString() = runTest {
        val updates = mutableListOf<String>()
        TextRevealer.reveal("") { updates.add(it) }

        assertTrue(updates.isEmpty())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testCancellation() = runTest {
        val input = "a".repeat(3000)
        val updates = mutableListOf<String>()
        var completed = false

        val job = launch {
            TextRevealer.reveal(input) { updates.add(it) }
            completed = true
        }

        advanceTimeBy(TextRevealer.TICK_MS * TextRevealer.MAX_CHUNK * 10)
        val updatesBeforeCancel = updates.size
        assertTrue(updatesBeforeCancel > 0)

        job.cancel()
        advanceUntilIdle()

        assertEquals(updatesBeforeCancel, updates.size)
        assertFalse(completed)
    }
}
