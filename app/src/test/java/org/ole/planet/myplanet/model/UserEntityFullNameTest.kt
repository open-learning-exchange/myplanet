package org.ole.planet.myplanet.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UserEntityFullNameTest {

    private fun user(first: String?, middle: String?, last: String?) =
        UserEntity(firstName = first, middleName = middle, lastName = last)

    @Test
    fun `missing middle name leaves a single space`() {
        assertEquals("Sau Tester", user("Sau", null, "Tester").getFullNameWithMiddleName())
        assertEquals("Sau Tester", user("Sau", "", "Tester").getFullNameWithMiddleName())
        assertEquals("Sau Tester", user("Sau", "  ", "Tester").getFullNameWithMiddleName())
    }

    @Test
    fun `middle name sits between first and last`() {
        assertEquals("Sau M Tester", user("Sau", "M", "Tester").getFullNameWithMiddleName())
    }

    @Test
    fun `null names are never printed as the word null`() {
        assertEquals("Tester", user(null, null, "Tester").getFullName())
        assertEquals("Sau", user("Sau", null, null).getFullName())
    }

    @Test
    fun `member with no names has an empty full name`() {
        assertEquals("", user("", "", "").getFullName())
        assertEquals("", user(null, null, null).getFullNameWithMiddleName())
    }

    @Test
    fun `stray whitespace around names is trimmed`() {
        assertEquals("Sau Tester", user(" Sau ", null, " Tester").getFullName())
    }
}
