package org.ole.planet.myplanet.repository

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Method

class ResourcesRepositoryImplTest {

    @Test
    fun testNormalizeText() {
        val repository = ResourcesRepositoryImpl(
            context = mockk(relaxed = true),
            databaseService = mockk(relaxed = true),
            realmDispatcher = mockk(relaxed = true),
            activitiesRepository = mockk(relaxed = true),
            settings = mockk(relaxed = true),
            sharedPrefManager = mockk(relaxed = true),
            ratingsRepository = mockk(relaxed = true),
            tagsRepository = mockk(relaxed = true),
            teamsRepositoryLazy = mockk(relaxed = true)
        )

        val method: Method = ResourcesRepositoryImpl::class.java.getDeclaredMethod("normalizeText", String::class.java)
        method.isAccessible = true

        assertEquals("hello world", method.invoke(repository, "HELLO World"))

        // Diacritics testing
        assertEquals("cafe", method.invoke(repository, "Café"))
        assertEquals("nino", method.invoke(repository, "Niño"))
        assertEquals("a e i o u", method.invoke(repository, "á é í ó ú"))
        assertEquals("c", method.invoke(repository, "ç"))
        assertEquals("aeiou", method.invoke(repository, "äëïöü"))
    }
}
