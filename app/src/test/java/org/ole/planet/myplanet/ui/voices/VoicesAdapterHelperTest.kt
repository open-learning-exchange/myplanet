package org.ole.planet.myplanet.ui.voices

import com.google.gson.JsonParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun stripAttachedImagesRemovesInlineCopyOfAttachment() {
        val message = "testing community labels   ![](resources/abc-123/post1.jpg)"
        assertEquals(
            "testing community labels",
            VoicesAdapterHelper.stripAttachedImages(message, setOf("abc-123"))
        )
    }

    @Test
    fun stripAttachedImagesKeepsImagesThatAreNotAttachments() {
        val message = "hi ![](resources/other/pic.jpg) ![alt](resources/abc-123/post1.jpg) there"
        assertEquals(
            "hi ![](resources/other/pic.jpg)  there",
            VoicesAdapterHelper.stripAttachedImages(message, setOf("abc-123"))
        )
    }

    @Test
    fun stripAttachedImagesLeavesMessageUntouchedWithoutAttachments() {
        val message = "plain text  "
        assertEquals(message, VoicesAdapterHelper.stripAttachedImages(message, emptySet()))
        assertEquals("", VoicesAdapterHelper.stripAttachedImages(null, setOf("abc")))
    }

    @Test
    fun attachedResourceIdsReadsResourceIdsFromImagesArray() {
        val images = JsonParser.parseString(
            """[{"resourceId":"a","filename":"x.jpg"},{"filename":"y.jpg"},{"resourceId":"b"}]"""
        ).asJsonArray
        assertEquals(setOf("a", "b"), VoicesAdapterHelper.attachedResourceIds(images))
        assertEquals(emptySet<String>(), VoicesAdapterHelper.attachedResourceIds(null))
    }

    @Test
    fun resourceIdFromDownloadUrlExtractsSegmentAfterResources() {
        assertEquals(
            "4136d1c6",
            VoicesAdapterHelper.resourceIdFromDownloadUrl("https://planet.example/resources/4136d1c6/post.jpg")
        )
        assertEquals(
            "abc",
            VoicesAdapterHelper.resourceIdFromDownloadUrl("http://10.0.0.1/db/resources/abc/file.pdf?rev=1")
        )
        assertNull(VoicesAdapterHelper.resourceIdFromDownloadUrl("https://planet.example/other/abc/file.pdf"))
        assertNull(VoicesAdapterHelper.resourceIdFromDownloadUrl("https://planet.example/resources/"))
        assertNull(VoicesAdapterHelper.resourceIdFromDownloadUrl(null))
    }

    @Test
    fun referencedResourceIdsIncludesInlineImagesWithoutAttachments() {
        val inlineOnly = org.ole.planet.myplanet.model.News().apply {
            message = "new voice\n![](resources/r1/Sector_V.gif)\n![](resources/r2/a.jpg)"
            images = "[]"
        }
        val attached = org.ole.planet.myplanet.model.News().apply {
            message = "hi"
            images = """[{"resourceId":"r3"},{"resourceId":"r4"}]"""
        }
        val brokenInline = org.ole.planet.myplanet.model.News().apply { message = "hh ![](resources/r5/)" }
        assertEquals(
            setOf("r1", "r2", "r3", "r4", "r5"),
            VoicesAdapterHelper.referencedResourceIds(listOf(inlineOnly, null, attached, brokenInline))
        )
    }

    @Test
    fun stripUnavailableImagesDropsOnlyImagesNotOnDisk() {
        val message = "Test\n\n![](resources/a/on.jpg)\n![](resources/b/off.jpg)\n![](resources/c/)"
        assertEquals(
            "Test\n\n![](resources/a/on.jpg)",
            VoicesAdapterHelper.stripUnavailableImages(message) { it == "a/on.jpg" }
        )
        assertEquals("plain", VoicesAdapterHelper.stripUnavailableImages("plain") { false })
    }
}
