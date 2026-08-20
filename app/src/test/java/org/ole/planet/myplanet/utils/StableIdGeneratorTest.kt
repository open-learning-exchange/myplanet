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
    fun testGenerateStringId_stableAndCollisionResistant() {
        val id1 = StableIdGenerator.generateStringId("course-1234")
        val id2 = StableIdGenerator.generateStringId("course-1235")
        val id3 = StableIdGenerator.generateStringId("course-1234")

        assertEquals(id1, id3)
        assertNotEquals(id1, id2)
    }
}
