package org.ole.planet.myplanet.ui.enterprises

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AttachmentPresenceCacheTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var cache: AttachmentPresenceCache

    @Before
    fun setUp() {
        cache = AttachmentPresenceCache(ttlMs = 5000L)
    }

    @Test
    fun testExists_nullFile_returnsFalse() {
        assertFalse(cache.exists(null, now = 1000L))
    }

    @Test
    fun testExists_cachesResultAndHonorsTtl() {
        val testFile = tempFolder.newFile("test.txt")

        // First check when file exists -> true
        assertTrue(cache.exists(testFile, now = 1000L))

        // Delete file on disk
        testFile.delete()

        // Within TTL (1000L + 4999L < 5000L TTL end at 6000L) -> returns cached true
        assertTrue(cache.exists(testFile, now = 5999L))

        // After TTL (1000L + 5000L = 6000L) -> re-checks disk and returns false
        assertFalse(cache.exists(testFile, now = 6000L))
    }

    @Test
    fun testClear_invalidatesCache() {
        val testFile = tempFolder.newFile("test_clear.txt")

        assertTrue(cache.exists(testFile, now = 1000L))

        testFile.delete()

        // Clearing cache forces fresh stat on disk
        cache.clear()

        assertFalse(cache.exists(testFile, now = 2000L))
    }
}
