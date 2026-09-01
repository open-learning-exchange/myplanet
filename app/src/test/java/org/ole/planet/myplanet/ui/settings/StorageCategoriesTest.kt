package org.ole.planet.myplanet.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.R

class StorageCategoriesTest {

    @Test
    fun `all contains the five categories in the canonical order`() {
        assertEquals(
            listOf(
                R.string.storage_videos,
                R.string.storage_audio,
                R.string.storage_pdfs,
                R.string.storage_images,
                R.string.storage_other
            ),
            StorageCategories.all.map { it.nameRes }
        )
    }

    @Test
    fun `only the last category is the empty catch-all`() {
        assertEquals(R.string.storage_other, StorageCategories.all[StorageCategories.OTHER_INDEX].nameRes)
        assertTrue(StorageCategories.all[StorageCategories.OTHER_INDEX].extensions.isEmpty())
        StorageCategories.all.dropLast(1).forEach {
            assertTrue("category $it should map extensions", it.extensions.isNotEmpty())
        }
    }

    @Test
    fun `allKnownExtensions is the union of the non-other categories`() {
        val expected = setOf(
            "mp4", "mkv", "avi", "webm", "mov", "3gp", "flv",
            "mp3", "wav", "ogg", "m4a", "flac", "aac", "opus",
            "pdf",
            "jpg", "jpeg", "png", "gif", "webp", "bmp"
        )
        assertEquals(expected, StorageCategories.allKnownExtensions)
    }

    @Test
    fun `indexOf resolves known extensions to their category index`() {
        assertEquals(0, StorageCategories.indexOf("mp4"))
        assertEquals(0, StorageCategories.indexOf("mkv"))
        assertEquals(1, StorageCategories.indexOf("mp3"))
        assertEquals(2, StorageCategories.indexOf("pdf"))
        assertEquals(3, StorageCategories.indexOf("jpg"))
    }

    @Test
    fun `indexOf falls back to the other category for unknown extensions`() {
        assertEquals(StorageCategories.OTHER_INDEX, StorageCategories.indexOf("zip"))
        assertEquals(StorageCategories.OTHER_INDEX, StorageCategories.indexOf("txt"))
        assertEquals(StorageCategories.OTHER_INDEX, StorageCategories.indexOf(""))
        assertEquals(StorageCategories.OTHER_INDEX, StorageCategories.indexOf("MKV"))
    }

    @Test
    fun `no extension is mapped to more than one category`() {
        val seen = mutableSetOf<String>()
        StorageCategories.all.dropLast(1).forEach { category ->
            category.extensions.forEach { ext ->
                assertFalse("extension '$ext' duplicated across categories", seen.contains(ext))
                seen.add(ext)
            }
        }
    }
}
