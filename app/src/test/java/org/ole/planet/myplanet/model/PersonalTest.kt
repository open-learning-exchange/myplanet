package org.ole.planet.myplanet.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PersonalTest {

    @Test
    fun testDefaultValues() {
        val personal = Personal()
        assertEquals("", personal.id)
        assertNull(personal._id)
        assertNull(personal._rev)
        assertFalse(personal.isUploaded)
        assertNull(personal.title)
        assertNull(personal.description)
        assertEquals(0L, personal.date)
        assertNull(personal.userId)
        assertNull(personal.userName)
        assertNull(personal.path)
    }
}
