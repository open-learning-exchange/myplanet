package org.ole.planet.myplanet.utils

import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StableIdGeneratorTest {
    @Test
    fun testGenerateStringId_nullOrEmpty() {
        assertEquals(RecyclerView.NO_ID, StableIdGenerator.generateStringId(null))
        assertEquals(RecyclerView.NO_ID, StableIdGenerator.generateStringId(""))
    }

    @Test
    fun testGenerateStringId_determinism() {
        val id1 = StableIdGenerator.generateStringId("course-1234")
        val id3 = StableIdGenerator.generateStringId("course-1234")

        assertEquals(id1, id3)
    }

    @Test
    fun testGenerateStringId_collisionResistance() {
        // "Aa" and "BB" have the same standard 32-bit java.lang.String.hashCode() (2112)
        val id1 = StableIdGenerator.generateStringId("Aa")
        val id2 = StableIdGenerator.generateStringId("BB")

        assertNotEquals(id1, id2)
    }

    @Test
    fun testGenerateFallbackId() {
        val item1 = Any()
        val item2 = Any()

        val fallbackId1 = StableIdGenerator.generateFallbackId(item1)
        val fallbackId1Again = StableIdGenerator.generateFallbackId(item1)
        val fallbackId2 = StableIdGenerator.generateFallbackId(item2)

        assertEquals(fallbackId1, fallbackId1Again)
        assertNotEquals(fallbackId1, fallbackId2)
        assertNotEquals(RecyclerView.NO_ID, fallbackId1)
        assertNotEquals(RecyclerView.NO_ID, fallbackId2)
    }

    private data class ValueItem(val name: String)

    private class IdentityItem

    @Test
    fun testGenerateFallbackId_equalInstancesShareId() {
        val first = ValueItem("unsynced-team")
        val second = ValueItem("unsynced-team")

        val firstId = StableIdGenerator.generateFallbackId(first)
        val secondId = StableIdGenerator.generateFallbackId(second)

        assertEquals(firstId, secondId)
        // Also pins both keys as reachable across the lookups above, since the cache holds them weakly.
        assertEquals(first, second)
    }

    @Test
    fun testGenerateFallbackId_identityInstancesGetDistinctIds() {
        val first = IdentityItem()
        val second = IdentityItem()

        val firstId = StableIdGenerator.generateFallbackId(first)
        val secondId = StableIdGenerator.generateFallbackId(second)

        assertNotEquals(firstId, secondId)
        assertNotEquals(first, second)
    }
}
