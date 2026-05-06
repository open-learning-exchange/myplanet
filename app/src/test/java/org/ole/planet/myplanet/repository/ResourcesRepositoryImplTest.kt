package org.ole.planet.myplanet.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class ResourcesRepositoryImplTest {

    @Test
    fun testNormalizeText() {
        // Happy paths
        assertEquals("hello world", ResourcesRepositoryImpl.normalizeText("HELLO World"))

        // Diacritics testing
        assertEquals("cafe", ResourcesRepositoryImpl.normalizeText("Café"))
        assertEquals("nino", ResourcesRepositoryImpl.normalizeText("Niño"))
        assertEquals("a e i o u", ResourcesRepositoryImpl.normalizeText("á é í ó ú"))
        assertEquals("c", ResourcesRepositoryImpl.normalizeText("ç"))
        assertEquals("aeiou", ResourcesRepositoryImpl.normalizeText("äëïöü"))
    }
}
