package org.ole.planet.myplanet.repository

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.services.SharedPrefManager

class CoursesRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val databaseService: DatabaseService = mockk(relaxed = true)
    private val testDispatcher = mainDispatcherRule.testDispatcher
    private val progressRepository: ProgressRepository = mockk(relaxed = true)
    private val activitiesRepository: ActivitiesRepository = mockk(relaxed = true)
    private val submissionsRepository: SubmissionsRepository = mockk(relaxed = true)
    private val tagsRepository: TagsRepository = mockk(relaxed = true)
    private val ratingsRepository: RatingsRepository = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)

    private val mockRealm: Realm = mockk(relaxed = true)
    private lateinit var repository: CoursesRepositoryImpl

    @Before
    fun setup() {
        io.mockk.coEvery { databaseService.withRealm<Any>(any()) } answers {
            val block = firstArg<io.realm.Realm.() -> Any>()
            block(mockRealm)
        }
        io.mockk.coEvery { databaseService.withRealmAsync<Any>(any()) } answers {
            val block = firstArg<(io.realm.Realm) -> Any>()
            block(mockRealm)
        }
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
        assertEquals("hello world", repository.normalizeText("HELLO World"))
        assertEquals("cafe", repository.normalizeText("Café"))
        assertEquals("nino", repository.normalizeText("Niño"))
        assertEquals("a e i o u", repository.normalizeText("á é í ó ú"))
        assertEquals("c", repository.normalizeText("ç"))
        assertEquals("aeiou", repository.normalizeText("äëïöü"))
    }

    @Test
    fun testMatchesAllParts() {
        assertTrue(repository.matchesAllParts("hello world", listOf("hello", "world")))
        assertFalse(repository.matchesAllParts("hello world", listOf("hello", "universe")))
        assertTrue(repository.matchesAllParts("the quick brown fox", listOf("quick", "fox")))
        assertTrue(repository.matchesAllParts("test", emptyList<String>()))
    }

    @Test
    fun `search empty query returns all courses`() = runTest {
        val mockData = mockk<RealmResults<RealmMyCourse>>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmMyCourse>>(relaxed = true)
        every { mockRealm.where(RealmMyCourse::class.java) } returns mockQuery
        every { mockQuery.findAll() } returns mockData
        every { mockRealm.copyFromRealm(mockData) } returns emptyList()

        val result = repository.search("")
        assertEquals(0, result.size)
        verify { mockQuery.findAll() }
    }

    @Test
    fun `search filters query parts before fetching and sorts startsWith before contains`() = runTest {
        val mockData = mockk<RealmResults<RealmMyCourse>>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmMyCourse>>(relaxed = true)
        every { mockRealm.where(RealmMyCourse::class.java) } returns mockQuery

        val startsWithCourse = mockk<RealmMyCourse>(relaxed = true) {
            every { courseTitleNormal } returns "math 101"
            every { courseTitle } returns "Math 101"
        }
        val containsCourse = mockk<RealmMyCourse>(relaxed = true) {
            every { courseTitleNormal } returns "basic math"
            every { courseTitle } returns "Basic Math"
        }
        val notMatchCourse = mockk<RealmMyCourse>(relaxed = true) {
            every { courseTitleNormal } returns "science"
            every { courseTitle } returns "Science"
        }

        every { mockData.iterator() } returns mutableListOf(containsCourse, notMatchCourse, startsWithCourse).iterator()
        every { mockQuery.contains("courseTitleNormal", "math", io.realm.Case.INSENSITIVE) } returns mockQuery
        every { mockQuery.findAll() } returns mockData
        every { mockRealm.copyFromRealm(any<List<RealmMyCourse>>()) } answers { firstArg() }

        val result = repository.search("Math")

        verify { mockQuery.contains("courseTitleNormal", "math", io.realm.Case.INSENSITIVE) }
        assertEquals(2, result.size)
        assertEquals(startsWithCourse, result[0])
        assertEquals(containsCourse, result[1])
    }

    @Test
    fun `search multi word matches all parts`() = runTest {
        val mockData = mockk<RealmResults<RealmMyCourse>>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmMyCourse>>(relaxed = true)
        every { mockRealm.where(RealmMyCourse::class.java) } returns mockQuery

        val matchCourse = mockk<RealmMyCourse>(relaxed = true) {
            every { courseTitleNormal } returns "basic math 101"
            every { courseTitle } returns "Basic Math 101"
        }
        val notMatchCourse = mockk<RealmMyCourse>(relaxed = true) {
            every { courseTitleNormal } returns "basic science 101"
            every { courseTitle } returns "Basic Science 101"
        }

        every { mockData.iterator() } returns mutableListOf(matchCourse, notMatchCourse).iterator()
        every { mockQuery.contains("courseTitleNormal", "basic", io.realm.Case.INSENSITIVE) } returns mockQuery
        every { mockQuery.contains("courseTitleNormal", "math", io.realm.Case.INSENSITIVE) } returns mockQuery
        every { mockQuery.findAll() } returns mockData
        every { mockRealm.copyFromRealm(any<List<RealmMyCourse>>()) } answers { firstArg() }

        val result = repository.search("Basic Math")

        verify { mockQuery.contains("courseTitleNormal", "basic", io.realm.Case.INSENSITIVE) }
        verify { mockQuery.contains("courseTitleNormal", "math", io.realm.Case.INSENSITIVE) }
        assertEquals(1, result.size)
        assertEquals(matchCourse, result[0])
    }
}
