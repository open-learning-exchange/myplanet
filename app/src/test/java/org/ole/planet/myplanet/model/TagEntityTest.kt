package org.ole.planet.myplanet.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagEntityTest {

    @Test
    fun getTagsArray_emptyList_returnsEmptyJsonArray() {
        val result = TagEntity.getTagsArray(emptyList())
        assertEquals(0, result.size())
    }

    @Test
    fun getTagsArray_idsInOrderWithDuplicates_preservesOrderAndDuplicates() {
        val tag1 = TagEntity().apply { _id = "tag1" }
        val tag2 = TagEntity().apply { _id = "tag2" }
        val tag1Duplicate = TagEntity().apply { _id = "tag1" }

        val result = TagEntity.getTagsArray(listOf(tag1, tag2, tag1Duplicate))

        assertEquals(3, result.size())
        assertEquals("tag1", result[0].asString)
        assertEquals("tag2", result[1].asString)
        assertEquals("tag1", result[2].asString)
    }

    @Test
    fun getTagsArray_nullId_yieldsJsonNull() {
        val tagWithNullId = TagEntity().apply { _id = null }
        val tagWithValidId = TagEntity().apply { _id = "tag1" }

        val result = TagEntity.getTagsArray(listOf(tagWithNullId, tagWithValidId))

        assertEquals(2, result.size())
        assertTrue(result[0].isJsonNull)
        assertEquals("tag1", result[1].asString)
    }
}
