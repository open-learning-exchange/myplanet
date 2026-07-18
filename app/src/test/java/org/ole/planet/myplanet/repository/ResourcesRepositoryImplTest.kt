package org.ole.planet.myplanet.repository

import android.content.Context
import com.google.gson.JsonParser
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.MyLibraryDao
import org.ole.planet.myplanet.data.room.dao.RemovedLogDao
import org.ole.planet.myplanet.data.room.dao.ResourceActivityDao
import org.ole.planet.myplanet.data.room.dao.SearchActivityDao
import org.ole.planet.myplanet.data.room.dao.legacy.TeamDao
import org.ole.planet.myplanet.data.room.dao.legacy.UserDao
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.SearchActivity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.Utilities

@OptIn(ExperimentalCoroutinesApi::class)
class ResourcesRepositoryImplTest {

    private val context: Context = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val activitiesRepository: ActivitiesRepository = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)
    private val ratingsRepository: RatingsRepository = mockk(relaxed = true)
    private val tagsRepository: TagsRepository = mockk(relaxed = true)
    private val searchActivityDao: SearchActivityDao = mockk(relaxed = true)
    private val resourceActivityDao: ResourceActivityDao = mockk(relaxed = true)
    private val removedLogDao: RemovedLogDao = mockk(relaxed = true)
    private val teamsRepositoryLazy: Lazy<TeamsRepository> = mockk(relaxed = true)
    private val teamsSyncRepositoryLazy: Lazy<TeamsSyncRepository> = mockk(relaxed = true)
    private val myLibraryDao: MyLibraryDao = mockk(relaxed = true)
    private val userDao: UserDao = mockk(relaxed = true)
    private val teamDao: TeamDao = mockk(relaxed = true)

    private lateinit var repository: ResourcesRepositoryImpl

    @Before
    fun setup() {
        Logger.getLogger("io.mockk").level = Level.OFF

        repository = ResourcesRepositoryImpl(
            context,
            activitiesRepository,
            sharedPrefManager,
            ratingsRepository,
            tagsRepository,
            searchActivityDao,
            resourceActivityDao,
            removedLogDao,
            teamsSyncRepositoryLazy,
            myLibraryDao,
            userDao,
            teamDao,
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
    fun `getAllLibraries returns list of MyLibrary`() = runTest {
        val mockLibrary = MyLibrary().apply { title = "Test Library" }
        coEvery { myLibraryDao.getAll() } returns listOf(mockLibrary)

        val result = repository.getAllLibraries()

        assertEquals(1, result.size)
        assertEquals("Test Library", result[0].title)
    }

    @Test
    fun `getLibraryItemById returns correct item`() = runTest {
        val mockLibrary = MyLibrary().apply { id = "id1"; title = "Item 1" }
        coEvery { myLibraryDao.getById("id1") } returns mockLibrary

        val result = repository.getLibraryItemById("id1")

        assertEquals("Item 1", result?.title)
    }

    @Test
    fun `search with empty query returns all public items`() = runTest {
        val lib1 = MyLibrary().apply { title = "Library 1" }
        val lib2 = MyLibrary().apply { title = "Library 2" }
        coEvery { myLibraryDao.getPublic() } returns listOf(lib1, lib2)

        val result = repository.search("", false, null)

        assertEquals(2, result.size)
    }

    @Test
    fun `search with isMyCourseLib true and userId null returns empty list`() = runTest {
        val result = repository.search("query", true, null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `search with query filters by title`() = runTest {
        val mathBook = MyLibrary().apply { title = "Math Book"; titleNormal = "math book" }
        val scienceBook = MyLibrary().apply { title = "Science Book"; titleNormal = "science book" }
        coEvery { myLibraryDao.getPublic() } returns listOf(mathBook, scienceBook)

        val result = repository.search("math", false, null)

        assertEquals(1, result.size)
        assertEquals("Math Book", result[0].title)
    }

    @Test
    fun `getEnrichedLibraries fetches public-not-user items when not my course lib`() = runTest {
        val lib1 = MyLibrary().apply { id = "1"; resourceId = "r1"; title = "Match" }
        coEvery { myLibraryDao.getPublicNotUserPattern(any()) } returns listOf(lib1)
        coEvery { ratingsRepository.getResourceRatings(any()) } returns HashMap()
        coEvery { tagsRepository.getTagsForResources(any()) } returns emptyMap()

        val result = repository.getEnrichedLibraries(false, "model123")

        assertEquals(1, result.size)
        assertEquals("Match", result[0].library.title)
        coVerify { myLibraryDao.getPublicNotUserPattern(any()) }
    }

    @Test
    fun `search empty query returns empty when no public libraries`() = runTest {
        coEvery { myLibraryDao.getPublic() } returns emptyList()

        val result = repository.search("", false, null)
        assertEquals(0, result.size)
    }

    @Test
    fun `search filters query parts and sorts startsWith before contains`() = runTest {
        val startsWithLib = MyLibrary().apply { title = "Ápple Tree"; titleNormal = "apple tree" }
        val containsLib = MyLibrary().apply { title = "Green Ápple"; titleNormal = "green apple" }
        val notMatchLib = MyLibrary().apply { title = "Banana"; titleNormal = "banana" }
        coEvery { myLibraryDao.getPublic() } returns listOf(containsLib, notMatchLib, startsWithLib)

        val result = repository.search("Apple", false, null)

        assertEquals(2, result.size)
        assertEquals(startsWithLib, result[0])
        assertEquals(containsLib, result[1])
    }

    @Test
    fun `search multi word matches all parts`() = runTest {
        val matchLib = MyLibrary().apply { title = "The Apple Tree"; titleNormal = "the apple tree" }
        val notMatchLib = MyLibrary().apply { title = "The Orange Tree"; titleNormal = "the orange tree" }
        coEvery { myLibraryDao.getPublic() } returns listOf(matchLib, notMatchLib)

        val result = repository.search("Ápple Tree", false, null)

        assertEquals(1, result.size)
        assertEquals(matchLib, result[0])
    }

    @Test
    fun `saveSearchActivity writes resource search activity to Room`() = runTest {
        val savedActivity = slot<SearchActivity>()

        repository.saveSearchActivity(
            userName = "learner",
            searchText = "physics",
            planetCode = "planet",
            parentCode = "parent",
            tags = emptyList(),
            subjects = setOf("science"),
            languages = setOf("en"),
            levels = setOf("beginner"),
            mediums = setOf("video")
        )

        coVerify(exactly = 1) { searchActivityDao.insert(capture(savedActivity)) }
        assertTrue(savedActivity.captured.id.isNotBlank())
        assertEquals("learner", savedActivity.captured.user)
        assertEquals("planet", savedActivity.captured.createdOn)
        assertEquals("parent", savedActivity.captured.parentCode)
        assertEquals("physics", savedActivity.captured.text)
        assertEquals("resources", savedActivity.captured.type)

        val filter = JsonParser.parseString(savedActivity.captured.filter).asJsonObject
        assertEquals(listOf("science"), filter.getAsJsonArray("subjects").map { it.asString })
        assertEquals(listOf("en"), filter.getAsJsonArray("language").map { it.asString })
        assertEquals(listOf("beginner"), filter.getAsJsonArray("level").map { it.asString })
        assertEquals(listOf("video"), filter.getAsJsonArray("mediaType").map { it.asString })
        assertTrue(filter.getAsJsonArray("tags").isEmpty)
    }
}
