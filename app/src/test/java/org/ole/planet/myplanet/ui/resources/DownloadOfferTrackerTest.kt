package org.ole.planet.myplanet.ui.resources

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DownloadOfferTrackerTest {

    @Before
    fun setUp() = DownloadOfferTracker.reset()

    @After
    fun tearDown() = DownloadOfferTracker.reset()

    @Test
    fun `the same resources are offered only once per session`() {
        assertTrue(DownloadOfferTracker.shouldOffer("user1", listOf("a", "b")))
        assertFalse("reopening myLibrary must not ask again", DownloadOfferTracker.shouldOffer("user1", listOf("a", "b")))
        assertFalse(DownloadOfferTracker.shouldOffer("user1", listOf("b")))
    }

    @Test
    fun `a resource not offered yet brings the suggestion back`() {
        DownloadOfferTracker.shouldOffer("user1", listOf("a"))

        assertTrue(DownloadOfferTracker.shouldOffer("user1", listOf("a", "new-from-sync")))
        assertFalse(DownloadOfferTracker.shouldOffer("user1", listOf("a", "new-from-sync")))
    }

    @Test
    fun `each user is offered their own resources`() {
        DownloadOfferTracker.shouldOffer("user1", listOf("a"))

        assertTrue(DownloadOfferTracker.shouldOffer("user2", listOf("a")))
    }
}
