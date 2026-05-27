package org.ole.planet.myplanet.repository

import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.services.SharedPrefManager
import java.lang.reflect.Method

@OptIn(ExperimentalCoroutinesApi::class)
class CoursesRepositoryImplTest {

    private val databaseService: DatabaseService = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val progressRepository: ProgressRepository = mockk(relaxed = true)
    private val activitiesRepository: ActivitiesRepository = mockk(relaxed = true)
    private val submissionsRepository: SubmissionsRepository = mockk(relaxed = true)
    private val tagsRepository: TagsRepository = mockk(relaxed = true)
    private val ratingsRepository: RatingsRepository = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)

    private lateinit var repository: CoursesRepositoryImpl

    @Before
    fun setup() {
        repository = CoursesRepositoryImpl(
            databaseService,
            testDispatcher,
            progressRepository,
            activitiesRepository,
            submissionsRepository,
            tagsRepository,
            ratingsRepository,
            sharedPrefManager
        )
    }

    @Test
    fun testNormalizeText() {
        val method: Method = CoursesRepositoryImpl::class.java.getDeclaredMethod("normalizeText", String::class.java)
        method.isAccessible = true

        assertEquals("hello world", method.invoke(repository, "HELLO World"))
        assertEquals("cafe", method.invoke(repository, "Café"))
        assertEquals("nino", method.invoke(repository, "Niño"))
        assertEquals("a e i o u", method.invoke(repository, "á é í ó ú"))
        assertEquals("c", method.invoke(repository, "ç"))
        assertEquals("aeiou", method.invoke(repository, "äëïöü"))
    }

    @Test
    fun testMatchesAllParts() {
        val method: Method = CoursesRepositoryImpl::class.java.getDeclaredMethod(
            "matchesAllParts",
            String::class.java,
            List::class.java
        )
        method.isAccessible = true

        assertTrue(method.invoke(repository, "hello world", listOf("hello", "world")) as Boolean)
        assertFalse(method.invoke(repository, "hello world", listOf("hello", "universe")) as Boolean)
        assertTrue(method.invoke(repository, "the quick brown fox", listOf("quick", "fox")) as Boolean)
        assertTrue(method.invoke(repository, "test", emptyList<String>()) as Boolean)
    }
}
