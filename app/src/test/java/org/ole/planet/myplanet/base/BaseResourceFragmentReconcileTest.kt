package org.ole.planet.myplanet.base

import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.model.MyLibrary

class BaseResourceFragmentReconcileTest {

    private fun library(address: String, offline: Boolean) = MyLibrary().apply {
        resourceRemoteAddress = address
        _rev = "1-a"
        if (offline) {
            resourceOffline = true
            downloadedRev = "1-a"
        }
    }

    @Test
    fun `queuedUrls leaves out resources already downloaded, which never report progress`() {
        val urls = BaseResourceFragment.queuedUrls(
            listOf(library("http://x/new", offline = false), library("http://x/done", offline = true))
        )

        assertEquals(listOf("http://x/new"), urls)
    }

    @Test
    fun `queuedUrls keeps an outdated offline copy, since it is downloaded again`() {
        val outdated = library("http://x/outdated", offline = true).apply { _rev = "2-b" }

        assertEquals(listOf("http://x/outdated"), BaseResourceFragment.queuedUrls(listOf(outdated)))
    }

    @Test
    fun `reconcile sorts pending downloads into finished, failed and still running`() {
        val pending = setOf("on-disk", "still-queued", "gone")
        val outcome = BaseResourceFragment.reconcile(
            pending,
            queued = setOf("still-queued", "on-disk")
        ) { it == "on-disk" }

        assertEquals(setOf("on-disk"), outcome.finished)
        assertEquals(setOf("gone"), outcome.failed)
    }

    @Test
    fun `reconcile reports nothing while every download is still queued`() {
        val outcome = BaseResourceFragment.reconcile(setOf("a", "b"), queued = setOf("a", "b")) { false }

        assertEquals(emptySet<String>(), outcome.finished)
        assertEquals(emptySet<String>(), outcome.failed)
    }
}
