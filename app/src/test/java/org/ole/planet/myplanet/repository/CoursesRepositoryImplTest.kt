package org.ole.planet.myplanet.repository
import org.ole.planet.myplanet.utils.Utilities

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
        assertEquals("hello world", Utilities.normalizeText("HELLO World"))
        assertEquals("cafe", Utilities.normalizeText("Café"))
        assertEquals("nino", Utilities.normalizeText("Niño"))
        assertEquals("a e i o u", Utilities.normalizeText("á é í ó ú"))
        assertEquals("c", Utilities.normalizeText("ç"))
        assertEquals("aeiou", Utilities.normalizeText("äëïöü"))
    }

    @Test
    fun testMatchesAllParts() {
        assertTrue(repository.matchesAllParts("hello world", listOf("hello", "world")))
        assertFalse(repository.matchesAllParts("hello world", listOf("hello", "universe")))
        assertTrue(repository.matchesAllParts("the quick brown fox", listOf("quick", "fox")))
        assertTrue(repository.matchesAllParts("test", emptyList<String>()))
    }
}
