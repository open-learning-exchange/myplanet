package org.ole.planet.myplanet.ui.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StorageBreakdownFragmentTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `scanStorage produces identical counts and total sizes for fixture tree`() {
        val rootDir = tempFolder.newFolder("ole")

        // Create test files
        // Videos (Index 0): mp4 (100 bytes), MKV (uppercase, 200 bytes)
        val file1 = File(rootDir, "video1.mp4").apply { writeBytes(ByteArray(100)) }
        val file2 = File(rootDir, "video2.MKV").apply { writeBytes(ByteArray(200)) }

        // Audio (Index 1): MP3 (uppercase, 50 bytes)
        val subDir = File(rootDir, "audio_folder").apply { mkdirs() }
        val file3 = File(subDir, "song.MP3").apply { writeBytes(ByteArray(50)) }

        // PDFs (Index 2): pdf (300 bytes)
        val file4 = File(rootDir, "document.pdf").apply { writeBytes(ByteArray(300)) }

        // Images (Index 3): PNG (uppercase, 40 bytes)
        val file5 = File(rootDir, "image.PNG").apply { writeBytes(ByteArray(40)) }

        // Other (Index 4): txt (unknown extension, 80 bytes), no extension (20 bytes)
        val file6 = File(rootDir, "notes.txt").apply { writeBytes(ByteArray(80)) }
        val file7 = File(rootDir, "README").apply { writeBytes(ByteArray(20)) }

        val fragment = StorageBreakdownFragment()
        val result = fragment.scanStorage(rootDir)

        val expectedTotal = 100L + 200L + 50L + 300L + 40L + 80L + 20L
        assertEquals(expectedTotal, result.totalBytes)

        // Videos: 2 files, 300 bytes
        assertEquals(2, result.counts[0])
        assertEquals(300L, result.sizes[0])

        // Audio: 1 file, 50 bytes
        assertEquals(1, result.counts[1])
        assertEquals(50L, result.sizes[1])

        // PDFs: 1 file, 300 bytes
        assertEquals(1, result.counts[2])
        assertEquals(300L, result.sizes[2])

        // Images: 1 file, 40 bytes
        assertEquals(1, result.counts[3])
        assertEquals(40L, result.sizes[3])

        // Other: 2 files, 100 bytes
        assertEquals(2, result.counts[4])
        assertEquals(100L, result.sizes[4])
    }

    @Test
    fun `scanStorage handles empty or nonexistent directory`() {
        val fragment = StorageBreakdownFragment()

        val emptyDir = tempFolder.newFolder("empty")
        val emptyResult = fragment.scanStorage(emptyDir)
        assertEquals(0L, emptyResult.totalBytes)
        assertEquals(0, emptyResult.counts.sum())

        val nonExistentDir = File(tempFolder.root, "non_existent")
        val nonExistentResult = fragment.scanStorage(nonExistentDir)
        assertEquals(0L, nonExistentResult.totalBytes)
        assertEquals(0, nonExistentResult.counts.sum())
    }
}
