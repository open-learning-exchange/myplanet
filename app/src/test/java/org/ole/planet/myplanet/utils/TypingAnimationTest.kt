package org.ole.planet.myplanet.utils

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil

class TypingAnimationTest {

    @Test
    fun testChunkSize() {
        assertEquals(1, TypingAnimation.chunkSize(0))
        assertEquals(1, TypingAnimation.chunkSize(1))
        assertEquals(1, TypingAnimation.chunkSize(199))
        assertEquals(2, TypingAnimation.chunkSize(400))
        assertEquals(8, TypingAnimation.chunkSize(1600))
        assertEquals(8, TypingAnimation.chunkSize(100_000))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testRevealLongString() = runTest {
        val input = "a".repeat(3000)
        val updates = mutableListOf<String>()
        TypingAnimation.reveal(input) { updates.add(it) }

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
        TypingAnimation.reveal(input) { updates.add(it) }

        assertEquals(listOf("a", "ab", "abc", "abcd", "abcde"), updates)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testRevealEmptyString() = runTest {
        val updates = mutableListOf<String>()
        TypingAnimation.reveal("") { updates.add(it) }

        assertTrue(updates.isEmpty())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testCancellation() = runTest {
        val input = "a".repeat(3000)
        val updates = mutableListOf<String>()
        var completed = false

        val job = launch {
            TypingAnimation.reveal(input) { updates.add(it) }
            completed = true
        }

        advanceTimeBy(TypingAnimation.TICK_MS * TypingAnimation.MAX_CHUNK * 10)
        val updatesBeforeCancel = updates.size
        assertTrue(updatesBeforeCancel > 0)

        job.cancel()
        advanceUntilIdle()

        assertEquals(updatesBeforeCancel, updates.size)
        assertFalse(completed)
    }
}
