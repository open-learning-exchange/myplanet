package org.ole.planet.myplanet.repository
import org.ole.planet.myplanet.utils.Utilities

import android.content.Context
import android.content.SharedPreferences
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import java.lang.reflect.Method
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.services.SharedPrefManager

@OptIn(ExperimentalCoroutinesApi::class)
class ResourcesRepositoryImplTest {

    private val context: Context = mockk(relaxed = true)
    private lateinit var databaseService: DatabaseService
    private lateinit var mockRealm: Realm
    private val testDispatcher = UnconfinedTestDispatcher()
    private val activitiesRepository: ActivitiesRepository = mockk(relaxed = true)
    private val settings: SharedPreferences = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)
    private val ratingsRepository: RatingsRepository = mockk(relaxed = true)
    private val tagsRepository: TagsRepository = mockk(relaxed = true)
    private val teamsRepositoryLazy: Lazy<TeamsRepository> = mockk(relaxed = true)
    private val teamsSyncRepositoryLazy: Lazy<TeamsSyncRepository> = mockk(relaxed = true)

    private lateinit var repository: ResourcesRepositoryImpl

    @Before
    fun setup() {
        Logger.getLogger("io.mockk").level = Level.OFF
        mockRealm = mockk(relaxed = true)
        databaseService = mockk(relaxed = true)

        coEvery { databaseService.withRealmAsync<Any>(any()) } answers {
            val operation = firstArg<(Realm) -> Any>()
            operation(mockRealm)
        }

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val operation = firstArg<(Realm) -> Unit>()
            operation(mockRealm)
        }

        repository = ResourcesRepositoryImpl(
            context,
            databaseService,
            testDispatcher,
            activitiesRepository,
            sharedPrefManager,
            ratingsRepository,
            tagsRepository,
            teamsRepositoryLazy,
            teamsSyncRepositoryLazy
        )
    }

    private fun mockQueryResults(vararg results: List<RealmMyLibrary>): RealmQuery<RealmMyLibrary> {
        val mockQuery = mockk<RealmQuery<RealmMyLibrary>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmMyLibrary>>(relaxed = true)

        every { mockRealm.where(RealmMyLibrary::class.java) } returns mockQuery

        // Setup fluent return
        every { mockQuery.equalTo(any<String>(), any<String>()) } returns mockQuery
        every { mockQuery.equalTo(any<String>(), any<Boolean>()) } returns mockQuery
        every { mockQuery.not() } returns mockQuery
        every { mockQuery.`in`(any<String>(), any<Array<String>>()) } returns mockQuery

        // Use the first result list or empty if none provided
        val resultList = results.firstOrNull() ?: emptyList()

        // Mock RealmResults iteration
        every { mockResults.iterator() } answers { resultList.toMutableList().iterator() }
        every { mockResults.size } returns resultList.size
        every { mockResults.isEmpty() } returns resultList.isEmpty()
        every { mockResults[any()] } answers { resultList[firstArg()] }
        every { mockResults.contains(any()) } answers { resultList.contains(firstArg()) }

        every { mockQuery.findAll() } returns mockResults
        every { mockQuery.findFirst() } returns resultList.firstOrNull()

        every { mockRealm.copyFromRealm(any<Iterable<RealmMyLibrary>>()) } answers {
            val arg = firstArg<Iterable<RealmMyLibrary>>()
            if (arg is RealmResults<*>) {
                resultList.toList()
            } else {
                arg.toList()
            }
        }
        every { mockRealm.copyFromRealm(any<RealmMyLibrary>()) } answers { firstArg() }

        return mockQuery
    }

    @Test
    fun testNormalizeText() {
        // Happy paths
        assertEquals("hello world", Utilities.normalizeText("HELLO World"))

        // Diacritics testing
        assertEquals("cafe", Utilities.normalizeText("Café"))
        assertEquals("nino", Utilities.normalizeText("Niño"))
        assertEquals("a e i o u", Utilities.normalizeText("á é í ó ú"))
        assertEquals("c", Utilities.normalizeText("ç"))
        assertEquals("aeiou", Utilities.normalizeText("äëïöü"))
    }


    @Test
    fun `getAllLibraries returns list of RealmMyLibrary`() = runTest {
        val mockLibrary = RealmMyLibrary().apply { title = "Test Library" }
        val mockQuery = mockQueryResults(listOf(mockLibrary))

        val result = repository.getAllLibraries()

        assertEquals(1, result.size)
        assertEquals("Test Library", result[0].title)
        verify(exactly = 0) { mockQuery.equalTo(any<String>(), any<Boolean>()) }
        verify(exactly = 0) { mockQuery.equalTo(any<String>(), any<String>()) }
    }

    @Test
    fun `getLibraryItemById returns correct item`() = runTest {
        val mockLibrary = RealmMyLibrary().apply { id = "id1"; title = "Item 1" }
        val mockQuery = mockQueryResults(listOf(mockLibrary))

        val result = repository.getLibraryItemById("id1")

        assertEquals("Item 1", result?.title)
        verify { mockQuery.equalTo("id", "id1") }
    }

    @Test
    fun `search with empty query returns all matched items`() = runTest {
        val mockLibrary1 = RealmMyLibrary().apply { title = "Library 1" }
        val mockLibrary2 = RealmMyLibrary().apply { title = "Library 2" }
        val mockQuery = mockQueryResults(listOf(mockLibrary1, mockLibrary2))

        val result = repository.search("", false, null)

        assertEquals(2, result.size)
        verify { mockQuery.equalTo("isPrivate", false) }
    }


    @Test
    fun `search with isMyCourseLib true and userId null returns empty list`() = runTest {
        val result = repository.search("query", true, null)
        assertTrue(result.isEmpty())
        // test mockQuery
    }

    @Test
    fun `search with query filters by title`() = runTest {
        val mockLibrary1 = RealmMyLibrary().apply { title = "Math Book" }
        val mockLibrary2 = RealmMyLibrary().apply { title = "Science Book" }
        val mockQuery = mockQueryResults(listOf(mockLibrary1, mockLibrary2))

        val result = repository.search("math", false, null)

        assertEquals(1, result.size)
        assertEquals("Math Book", result[0].title)
    }

    @Test
    fun `getEnrichedLibraries returns correctly filtered items when not my course lib`() = runTest {
        // We will simulate the realm query builder matching logic for `getEnrichedLibraries`
        // In reality, the query in the repository uses `isNotNull("userId")` and `not().equalTo("userId", modelId)`

        // We need to setup mockQuery to record calls
        val mockLibrary1 = RealmMyLibrary().apply { id = "1"; title = "Match" }
        val mockQuery = mockQueryResults(listOf(mockLibrary1))

        repository.getEnrichedLibraries(false, "model123")

        // Verify the correct predicates were applied to the query
        verify { mockQuery.equalTo("isPrivate", false) }
        verify { mockQuery.not() }
        verify { mockQuery.equalTo("userId", "model123") }
    }

    @Test
    fun `search empty query returns all public libraries`() = runTest {
        val mockData = mockk<RealmResults<RealmMyLibrary>>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmMyLibrary>>(relaxed = true)
        every { mockRealm.where(RealmMyLibrary::class.java) } returns mockQuery
        every { mockQuery.equalTo("isPrivate", false) } returns mockQuery
        every { mockQuery.findAll() } returns mockData
        every { mockRealm.copyFromRealm(mockData) } returns emptyList()

        val result = repository.search("", false, null)
        assertEquals(0, result.size)
        verify { mockQuery.findAll() }
    }

    @Test
    fun `search filters query parts before fetching and sorts startsWith before contains`() = runTest {
        val mockData = mockk<RealmResults<RealmMyLibrary>>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmMyLibrary>>(relaxed = true)
        every { mockRealm.where(RealmMyLibrary::class.java) } returns mockQuery
        every { mockQuery.equalTo("isPrivate", false) } returns mockQuery

        val startsWithLib = mockk<RealmMyLibrary>(relaxed = true) {
            every { title } returns "Ápple Tree"
        }
        val containsLib = mockk<RealmMyLibrary>(relaxed = true) {
            every { title } returns "Green Ápple"
        }
        val notMatchLib = mockk<RealmMyLibrary>(relaxed = true) {
            every { title } returns "Banana"
        }

        every { mockData.iterator() } returns mutableListOf(containsLib, notMatchLib, startsWithLib).iterator()
        every { mockQuery.contains("titleNormal", "apple", io.realm.Case.INSENSITIVE) } returns mockQuery
        every { mockQuery.findAll() } returns mockData
        every { mockRealm.copyFromRealm(any<List<RealmMyLibrary>>()) } answers { firstArg() }

        val result = repository.search("Apple", false, null)

        verify { mockQuery.contains("titleNormal", "apple", io.realm.Case.INSENSITIVE) }
        assertEquals(2, result.size)
        assertEquals(startsWithLib, result[0])
        assertEquals(containsLib, result[1])
    }

    @Test
    fun `search multi word matches all parts`() = runTest {
        val mockData = mockk<RealmResults<RealmMyLibrary>>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmMyLibrary>>(relaxed = true)
        every { mockRealm.where(RealmMyLibrary::class.java) } returns mockQuery
        every { mockQuery.equalTo("isPrivate", false) } returns mockQuery

        val matchLib = mockk<RealmMyLibrary>(relaxed = true) {
            every { title } returns "The Apple Tree"
        }
        val notMatchLib = mockk<RealmMyLibrary>(relaxed = true) {
            every { title } returns "The Orange Tree"
        }

        every { mockData.iterator() } returns mutableListOf(matchLib, notMatchLib).iterator()
        every { mockQuery.contains("titleNormal", "apple", io.realm.Case.INSENSITIVE) } returns mockQuery
        every { mockQuery.contains("titleNormal", "tree", io.realm.Case.INSENSITIVE) } returns mockQuery
        every { mockQuery.findAll() } returns mockData
        every { mockRealm.copyFromRealm(any<List<RealmMyLibrary>>()) } answers { firstArg() }

        val result = repository.search("Ápple Tree", false, null)

        verify { mockQuery.contains("titleNormal", "apple", io.realm.Case.INSENSITIVE) }
        verify { mockQuery.contains("titleNormal", "tree", io.realm.Case.INSENSITIVE) }
        assertEquals(1, result.size)
        assertEquals(matchLib, result[0])
    }
}
