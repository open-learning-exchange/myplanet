package org.ole.planet.myplanet.base

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DownloadSuggestionsTest {

    @Before
    fun setUp() = DownloadSuggestions.reset()

    @After
    fun tearDown() = DownloadSuggestions.reset()

    @Test
    fun `the same resources are offered only once per session`() {
        assertTrue(DownloadSuggestions.shouldOffer("user1", listOf("a", "b")))
        assertFalse("reopening myLibrary must not ask again", DownloadSuggestions.shouldOffer("user1", listOf("a", "b")))
        assertFalse(DownloadSuggestions.shouldOffer("user1", listOf("b")))
    }

    @Test
    fun `a resource not offered yet brings the suggestion back`() {
        DownloadSuggestions.shouldOffer("user1", listOf("a"))

        assertTrue(DownloadSuggestions.shouldOffer("user1", listOf("a", "new-from-sync")))
        assertFalse(DownloadSuggestions.shouldOffer("user1", listOf("a", "new-from-sync")))
    }

    @Test
    fun `each user is offered their own resources`() {
        DownloadSuggestions.shouldOffer("user1", listOf("a"))

        assertTrue(DownloadSuggestions.shouldOffer("user2", listOf("a")))
    }
}
