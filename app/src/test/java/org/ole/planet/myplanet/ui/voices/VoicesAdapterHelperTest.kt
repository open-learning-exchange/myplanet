package org.ole.planet.myplanet.ui.voices

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.TextRevealer

@OptIn(ExperimentalCoroutinesApi::class)
class VoicesAdapterHelperTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    @Test
    fun testOnAnimateTypingLongReply() = runTest {
        val testScope = TestScope(mainDispatcherRule.testDispatcher)
        val animateTyping = VoicesAdapterHelper.createOnAnimateTyping(testScope, dispatcherProvider)

        val input = "a".repeat(3000)
        val updates = mutableListOf<String>()
        var completeCount = 0

        animateTyping(input, { updates.add(it) }, { completeCount++ })
        testScope.advanceUntilIdle()

        assertTrue("Expected onUpdate calls <= 375, but was ${updates.size}", updates.size <= 375)
        assertTrue("Expected onUpdate calls > 0", updates.size > 0)
        assertEquals(input, updates.last())
        assertEquals(1, completeCount)
    }

    @Test
    fun testOnAnimateTypingEmptyString() = runTest {
        val testScope = TestScope(mainDispatcherRule.testDispatcher)
        val animateTyping = VoicesAdapterHelper.createOnAnimateTyping(testScope, dispatcherProvider)

        val updates = mutableListOf<String>()
        var completeCount = 0

        animateTyping("", { updates.add(it) }, { completeCount++ })
        testScope.advanceUntilIdle()

        assertTrue("Updates should be empty for empty string", updates.isEmpty())
        assertEquals(1, completeCount)
    }

    @Test
    fun testOnAnimateTypingCancellation() = runTest {
        val testScope = TestScope(mainDispatcherRule.testDispatcher)
        val animateTyping = VoicesAdapterHelper.createOnAnimateTyping(testScope, dispatcherProvider)

        val input = "a".repeat(3000)
        val updates = mutableListOf<String>()
        var completed = false

        val cancelClosure = animateTyping(input, { updates.add(it) }, { completed = true })

        testScope.advanceTimeBy(TextRevealer.TICK_MS * TextRevealer.MAX_CHUNK * 10)
        val updatesBeforeCancel = updates.size
        assertTrue(updatesBeforeCancel > 0)

        cancelClosure()
        testScope.advanceUntilIdle()

        assertEquals(updatesBeforeCancel, updates.size)
        assertFalse("onComplete should never fire after cancellation", completed)
    }
}
