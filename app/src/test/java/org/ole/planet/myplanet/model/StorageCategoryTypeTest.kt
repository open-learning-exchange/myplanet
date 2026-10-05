package org.ole.planet.myplanet.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageCategoryTypeTest {

    @Test
    fun `entries are in the canonical order`() {
        assertEquals(
            listOf(
                StorageCategoryType.VIDEOS,
                StorageCategoryType.AUDIO,
                StorageCategoryType.PDFS,
                StorageCategoryType.IMAGES,
                StorageCategoryType.OTHER
            ),
            StorageCategoryType.entries.toList()
        )
    }

    @Test
    fun `only the last category is the empty catch-all`() {
        assertEquals(StorageCategoryType.entries.lastIndex, StorageCategoryType.OTHER_INDEX)
        assertTrue(StorageCategoryType.OTHER.extensions.isEmpty())
        StorageCategoryType.entries.dropLast(1).forEach {
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
        assertEquals(expected, StorageCategoryType.allKnownExtensions)
    }

    @Test
    fun `indexOf resolves known extensions to their category index`() {
        assertEquals(0, StorageCategoryType.indexOf("mp4"))
        assertEquals(0, StorageCategoryType.indexOf("mkv"))
        assertEquals(1, StorageCategoryType.indexOf("mp3"))
        assertEquals(2, StorageCategoryType.indexOf("pdf"))
        assertEquals(3, StorageCategoryType.indexOf("jpg"))
    }

    @Test
    fun `indexOf is case-insensitive for known extensions`() {
        assertEquals(3, StorageCategoryType.indexOf("JPG"))
        assertEquals(3, StorageCategoryType.indexOf("Jpg"))
        assertEquals(3, StorageCategoryType.indexOf("jpg"))
        assertEquals(2, StorageCategoryType.indexOf("PDF"))
    }

    @Test
    fun `indexOf falls back to the other category for unknown extensions`() {
        assertEquals(StorageCategoryType.OTHER_INDEX, StorageCategoryType.indexOf("zip"))
        assertEquals(StorageCategoryType.OTHER_INDEX, StorageCategoryType.indexOf("txt"))
        assertEquals(StorageCategoryType.OTHER_INDEX, StorageCategoryType.indexOf(""))
        assertEquals(StorageCategoryType.OTHER_INDEX, StorageCategoryType.indexOf("xyz"))
        assertEquals(StorageCategoryType.OTHER_INDEX, StorageCategoryType.indexOf("XYZ"))
    }

    @Test
    fun `no extension is mapped to more than one category`() {
        val seen = mutableSetOf<String>()
        StorageCategoryType.entries.forEach { category ->
            category.extensions.forEach { ext ->
                assertFalse("extension '$ext' duplicated across categories", seen.contains(ext))
                seen.add(ext)
            }
        }
    }
}
