package org.ole.planet.myplanet.utils

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PartialDownloadCleanerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val now = 1_000_000_000_000L

    private fun file(dir: File, name: String, bytes: Int, modified: Long = now): File =
        File(dir, name).apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(bytes))
            setLastModified(modified)
        }

    @Test
    fun `isPartialFile matches the partial file and its sidecars only`() {
        assertTrue(PartialDownloadCleaner.isPartialFile("video.mp4.tmp"))
        assertTrue(PartialDownloadCleaner.isPartialFile("video.mp4.tmp.etag"))
        assertTrue(PartialDownloadCleaner.isPartialFile("video.mp4.tmp.size"))
        assertFalse(PartialDownloadCleaner.isPartialFile("video.mp4"))
        assertFalse(PartialDownloadCleaner.isPartialFile("notes.tmpl"))
    }

    @Test
    fun `summarize counts downloads, not sidecar files`() {
        val ole = temporaryFolder.newFolder("ole")
        file(File(ole, "res1"), "video.mp4.tmp", 100)
        file(File(ole, "res1"), "video.mp4.tmp.etag", 10)
        file(File(ole, "res2"), "book.pdf.tmp", 50)
        file(File(ole, "res2"), "done.pdf", 999)

        assertEquals(PartialDownloadCleaner.Summary(160L, 2), PartialDownloadCleaner.summarize(ole))
    }

    @Test
    fun `delete keeps recently written partials and removes older ones with their sidecars`() {
        val ole = temporaryFolder.newFolder("ole")
        val res = File(ole, "res1")
        val old = now - PartialDownloadCleaner.STALE_AFTER_MILLIS - 1
        val stale = file(res, "old.mp4.tmp", 100, modified = old)
        val staleEtag = file(res, "old.mp4.tmp.etag", 10, modified = old)
        val active = file(res, "new.mp4.tmp", 100, modified = now)
        val activeEtag = file(res, "new.mp4.tmp.etag", 10, modified = old)
        val orphanSize = file(res, "gone.mp4.tmp.size", 4)
        val finished = file(res, "done.mp4", 500, modified = old)

        val freed = PartialDownloadCleaner.delete(ole, now, PartialDownloadCleaner.STALE_AFTER_MILLIS)

        assertEquals(114L, freed)
        assertFalse(stale.exists())
        assertFalse(staleEtag.exists())
        assertFalse(orphanSize.exists())
        assertTrue("a partial being written right now must survive", active.exists())
        assertTrue("its validator follows the partial's age, not its own", activeEtag.exists())
        assertTrue("finished downloads are never touched", finished.exists())
    }
}
