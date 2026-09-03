package org.ole.planet.myplanet.repository

import android.content.Context
import com.google.gson.JsonParser
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.data.room.dao.MyLibraryDao
import org.ole.planet.myplanet.data.room.dao.RemovedLogDao
import org.ole.planet.myplanet.data.room.dao.ResourceActivityDao
import org.ole.planet.myplanet.data.room.dao.ResourceTitleProjection
import org.ole.planet.myplanet.data.room.dao.SearchActivityDao
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.SearchActivity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.DownloadUtils
import org.ole.planet.myplanet.utils.FileUtils
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
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val userSessionManager: UserSessionManager = mockk(relaxed = true)
    private val configurationsRepository: ConfigurationsRepository = mockk(relaxed = true)
    private val dispatcherProvider: DispatcherProvider = mockk(relaxed = true)

    @get:Rule
    val temporaryFolder = TemporaryFolder()

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
            userRepository,
            teamsRepositoryLazy,
            userSessionManager,
            configurationsRepository,
            dispatcherProvider
        )
        every { dispatcherProvider.io } returns testDispatcher
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
    fun `setUserLibrary returns null when user is not logged in`() = runTest {
        coEvery { userRepository.getUserModel() } returns null

        val result = repository.setUserLibrary("res-id", true)

        assertEquals(null, result)
        coVerify(exactly = 0) { myLibraryDao.getByResourceId(any()) }
    }

    @Test
    fun `setUserLibrary returns existing library and no-ops when already added`() = runTest {
        val mockUser = org.ole.planet.myplanet.model.UserEntity().apply { id = "user-123" }
        coEvery { userRepository.getUserModel() } returns mockUser

        val mockLibrary = MyLibrary().apply {
            id = "res-id"
            userId = listOf("user-123")
        }
        coEvery { myLibraryDao.getByResourceId("res-id") } returns mockLibrary

        val result = repository.setUserLibrary("res-id", true)

        assertEquals(mockLibrary, result)
        coVerify(exactly = 0) { myLibraryDao.upsert(any()) }
    }

    @Test
    fun `setUserLibrary returns existing library and no-ops when already removed`() = runTest {
        val mockUser = org.ole.planet.myplanet.model.UserEntity().apply { id = "user-123" }
        coEvery { userRepository.getUserModel() } returns mockUser

        val mockLibrary = MyLibrary().apply {
            id = "res-id"
            userId = emptyList()
        }
        coEvery { myLibraryDao.getByResourceId("res-id") } returns mockLibrary

        val result = repository.setUserLibrary("res-id", false)

        assertEquals(mockLibrary, result)
        coVerify(exactly = 0) { myLibraryDao.upsert(any()) }
    }

    @Test
    fun `markResourceOfflineByUrl marks resource offline when relative path matches entry file`() = runTest {
        val library = MyLibrary().apply {
            id = "res1"
            resourceId = "res1"
            openWhichFile = "index.html"
            resourceLocalAddress = null
            _rev = "2-abc"
        }
        coEvery { myLibraryDao.getByResourceId("res1") } returns library
        coEvery { myLibraryDao.getByLocalAddress(any()) } returns emptyList()

        val url = "http://example.com/resources/res1/index.html"
        mockkObject(FileUtils)
        try {
            every { FileUtils.getFileNameFromUrl(url) } returns "index.html"
            every { FileUtils.getIdFromUrl(url) } returns "res1"
            every { FileUtils.getResourceRelativePathFromUrl(url) } returns "index.html"

            repository.markResourceOfflineByUrl(url)

            assertTrue(library.resourceOffline)
            assertEquals("2-abc", library.downloadedRev)
            assertEquals("index.html", library.resourceLocalAddress)
            coVerify { myLibraryDao.upsert(library) }
        } finally {
            unmockkObject(FileUtils)
        }
    }

    @Test
    fun `markResourceOfflineByUrl does not mark offline for a non-entry html asset`() = runTest {
        val library = MyLibrary().apply {
            id = "res1"
            resourceId = "res1"
            openWhichFile = "index.html"
            resourceLocalAddress = null
        }
        coEvery { myLibraryDao.getByResourceId("res1") } returns library
        coEvery { myLibraryDao.getByLocalAddress(any()) } returns emptyList()

        val url = "http://example.com/resources/res1/style.css"
        mockkObject(FileUtils)
        try {
            every { FileUtils.getFileNameFromUrl(url) } returns "style.css"
            every { FileUtils.getIdFromUrl(url) } returns "res1"
            every { FileUtils.getResourceRelativePathFromUrl(url) } returns "style.css"

            repository.markResourceOfflineByUrl(url)

            assertFalse(library.resourceOffline)
            coVerify(exactly = 0) { myLibraryDao.upsert(any()) }
        } finally {
            unmockkObject(FileUtils)
        }
    }

    @Test
    fun `reconcileHtmlResourceOffline returns early when already offline`() = runTest {
        val library = MyLibrary().apply {
            id = "res1"
            resourceId = "res1"
            resourceOffline = true
            _rev = "3-xyz"
            downloadedRev = "3-xyz"
        }
        coEvery { myLibraryDao.getByResourceId("res1") } returns library

        repository.reconcileHtmlResourceOffline("res1")

        coVerify(exactly = 0) { myLibraryDao.upsert(any()) }
    }

    @Test
    fun `reconcileHtmlResourceOffline does not mark offline when entry file is missing from disk`() = runTest {
        val library = MyLibrary().apply {
            id = "res1"
            resourceId = "res1"
            openWhichFile = "index.html"
            resourceOffline = false
        }
        coEvery { myLibraryDao.getByResourceId("res1") } returns library

        val baseDir = kotlin.io.path.createTempDirectory("resources-repo-test").toFile()
        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.getExternalFilesDir(null) } returns baseDir
        mockkObject(MainApplication)
        try {
            every { MainApplication.context } returns mockContext

            repository.reconcileHtmlResourceOffline("res1")

            assertFalse(library.resourceOffline)
            coVerify(exactly = 0) { myLibraryDao.upsert(any()) }
        } finally {
            unmockkObject(MainApplication)
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun `reconcileHtmlResourceOffline marks offline when entry file exists on disk`() = runTest {
        val library = MyLibrary().apply {
            id = "res1"
            resourceId = "res1"
            openWhichFile = "index.html"
            resourceOffline = false
            _rev = "1-abc"
        }
        coEvery { myLibraryDao.getByResourceId("res1") } returns library

        val baseDir = kotlin.io.path.createTempDirectory("resources-repo-test").toFile()
        File(baseDir, "ole/res1").apply { mkdirs() }
        File(baseDir, "ole/res1/index.html").writeText("<html></html>")
        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.getExternalFilesDir(null) } returns baseDir
        mockkObject(MainApplication)
        try {
            every { MainApplication.context } returns mockContext

            repository.reconcileHtmlResourceOffline("res1")

            assertTrue(library.resourceOffline)
            assertEquals("1-abc", library.downloadedRev)
            assertEquals("index.html", library.resourceLocalAddress)
            coVerify { myLibraryDao.upsert(library) }
        } finally {
            unmockkObject(MainApplication)
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun `setUserLibrary returns updated library on successful toggle`() = runTest {
        val mockUser = org.ole.planet.myplanet.model.UserEntity().apply { id = "user-123" }
        coEvery { userRepository.getUserModel() } returns mockUser

        val mockLibrary = MyLibrary().apply {
            id = "res-id"
            userId = mutableListOf()
        }

        // Mock the lookups
        coEvery { myLibraryDao.getByResourceId("res-id") } returns mockLibrary
        coEvery { myLibraryDao.getById("res-id") } returns mockLibrary

        val result = repository.setUserLibrary("res-id", true)

        // updateUserLibrary mutates and calls upsert
        coVerify { myLibraryDao.upsert(mockLibrary) }
        assertTrue(result?.userId?.contains("user-123") == true)
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

        val querySlot = slot<androidx.sqlite.db.SupportSQLiteQuery>()
        coEvery { myLibraryDao.filterByTitleNormal(capture(querySlot)) } returns listOf(mathBook)

        val result = repository.search("math", false, null)

        assertEquals(1, result.size)
        assertEquals("Math Book", result[0].title)

        val capturedQuery = querySlot.captured
        assertTrue(capturedQuery.sql.contains("titleNormal LIKE ? ESCAPE '\\'"))

        val bindArgs = mutableMapOf<Int, Any?>()
        capturedQuery.bindTo(object : androidx.sqlite.db.SupportSQLiteProgram {
            override fun bindNull(index: Int) { bindArgs[index] = null }
            override fun bindLong(index: Int, value: Long) { bindArgs[index] = value }
            override fun bindDouble(index: Int, value: Double) { bindArgs[index] = value }
            override fun bindString(index: Int, value: String) { bindArgs[index] = value }
            override fun bindBlob(index: Int, value: ByteArray) { bindArgs[index] = value }
            override fun clearBindings() {}
            override fun close() {}
        })

        assertEquals("%math%", bindArgs[1])
    }

    @Test
    fun `getResourceListModels fetches public-not-user items when not my course lib`() = runTest {
        val lib1 = MyLibrary().apply { id = "1"; resourceId = "r1"; title = "Match" }
        coEvery { myLibraryDao.getPublicNotUserPattern(any()) } returns listOf(lib1)
        coEvery { ratingsRepository.getResourceRatings(any()) } returns HashMap()
        coEvery { tagsRepository.getTagsForResources(any()) } returns emptyMap()

        val result = repository.getResourceListModels(false, "model123")

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

        val querySlot = slot<androidx.sqlite.db.SupportSQLiteQuery>()
        coEvery { myLibraryDao.filterByTitleNormal(capture(querySlot)) } returns listOf(startsWithLib, containsLib)

        val result = repository.search("Apple", false, null)

        assertEquals(2, result.size)
        assertEquals(startsWithLib, result[0])
        assertEquals(containsLib, result[1])

        val capturedQuery = querySlot.captured
        assertTrue(capturedQuery.sql.contains("titleNormal LIKE ? ESCAPE '\\'"))
    }

    @Test
    fun `search multi word matches all parts`() = runTest {
        val matchLib = MyLibrary().apply { title = "The Apple Tree"; titleNormal = "the apple tree" }
        val notMatchLib = MyLibrary().apply { title = "The Orange Tree"; titleNormal = "the orange tree" }

        val querySlot = slot<androidx.sqlite.db.SupportSQLiteQuery>()
        coEvery { myLibraryDao.filterByTitleNormal(capture(querySlot)) } returns listOf(matchLib)

        val result = repository.search("Ápple Tree", false, null)

        assertEquals(1, result.size)
        assertEquals(matchLib, result[0])

        val capturedQuery = querySlot.captured
        assertTrue(capturedQuery.sql.contains("titleNormal LIKE ? ESCAPE '\\'"))
    }

    @Test
    fun `search properly escapes wildcards in query`() = runTest {
        val querySlot = slot<androidx.sqlite.db.SupportSQLiteQuery>()
        coEvery { myLibraryDao.filterByTitleNormal(capture(querySlot)) } returns emptyList()

        repository.search("100% _real_ \\deal", false, null)

        val bindArgs = mutableMapOf<Int, Any?>()
        querySlot.captured.bindTo(object : androidx.sqlite.db.SupportSQLiteProgram {
            override fun bindNull(index: Int) { bindArgs[index] = null }
            override fun bindLong(index: Int, value: Long) { bindArgs[index] = value }
            override fun bindDouble(index: Int, value: Double) { bindArgs[index] = value }
            override fun bindString(index: Int, value: String) { bindArgs[index] = value }
            override fun bindBlob(index: Int, value: ByteArray) { bindArgs[index] = value }
            override fun clearBindings() {}
            override fun close() {}
        })

        // SQLite binds are 1-indexed.
        assertEquals("%100\\%%", bindArgs[1])
        assertEquals("%\\_real\\_%", bindArgs[2])
        assertEquals("%\\\\deal%", bindArgs[3])
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

    @Test
    fun `getRecentResources returns flow from dao with correct pattern`() = runTest {
        val userId = "testUser123"
        val expectedPattern = "%\"testUser123\"%"
        val expectedList = listOf(mockk<MyLibrary>())

        every { myLibraryDao.getRecentForUserPatternFlow(expectedPattern) } returns flowOf(expectedList)

        val result = repository.getRecentResources(userId).first()

        assertEquals(expectedList, result)
    }

    @Test
    fun `getRecentResources deduplicates byte-identical flow emissions`() = runTest {
        val l1 = MyLibrary().apply { id = "l1"; _rev = "rev1"; resourceOffline = false; setUserId("u1") }
        val l2 = MyLibrary().apply { id = "l1"; _rev = "rev1"; resourceOffline = false; setUserId("u1") }
        every { myLibraryDao.getRecentForUserPatternFlow(any()) } returns flowOf(listOf(l1), listOf(l2))

        val emissions = mutableListOf<List<MyLibrary>>()
        repository.getRecentResources("u1").collect { emissions.add(it) }

        assertEquals(1, emissions.size)
    }

    @Test
    fun `getPendingDownloads returns flow from dao with correct pattern`() = runTest {
        val userId = "testUser123"
        val expectedPattern = "%\"testUser123\"%"
        val expectedList = listOf("lib1")

        every { myLibraryDao.getPendingDownloadsForUserPatternFlow(expectedPattern) } returns flowOf(expectedList)

        val result = repository.getPendingDownloads(userId).first()

        assertEquals(expectedList, result)
    }

    @Test
    fun `getPendingDownloads deduplicates byte-identical flow emissions`() = runTest {
        every { myLibraryDao.getPendingDownloadsForUserPatternFlow(any()) } returns flowOf(listOf("d1", "d2"), listOf("d1", "d2"))

        val emissions = mutableListOf<List<String>>()
        repository.getPendingDownloads("u1").collect { emissions.add(it) }

        assertEquals(1, emissions.size)
    }

    @Test
    fun `getLibraryItemByResourceId returns item by resourceId`() = runTest {
        val resourceId = "res123"
        val expectedLib = MyLibrary().apply { id = "id1" }
        coEvery { myLibraryDao.getByResourceId(resourceId) } returns expectedLib

        val result = repository.getLibraryItemByResourceId(resourceId)

        assertEquals(expectedLib, result)
        coVerify(exactly = 1) { myLibraryDao.getByResourceId(resourceId) }
        coVerify(exactly = 0) { myLibraryDao.getByUnderscoreId(any()) }
    }

    @Test
    fun `getLibraryItemByResourceId falls back to getByUnderscoreId if getByResourceId returns null`() = runTest {
        val resourceId = "res123"
        val expectedLib = MyLibrary().apply { id = "id2" }
        coEvery { myLibraryDao.getByResourceId(resourceId) } returns null
        coEvery { myLibraryDao.getByUnderscoreId(resourceId) } returns expectedLib

        val result = repository.getLibraryItemByResourceId(resourceId)

        assertEquals(expectedLib, result)
        coVerify(exactly = 1) { myLibraryDao.getByResourceId(resourceId) }
        coVerify(exactly = 1) { myLibraryDao.getByUnderscoreId(resourceId) }
    }

    @Test
    fun `getLibraryItemsByIds returns empty list if ids is empty`() = runTest {
        val result = repository.getLibraryItemsByIds(emptyList())

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { myLibraryDao.getByUnderscoreIds(any()) }
    }

    @Test
    fun `getLibraryItemsByIds returns items from dao`() = runTest {
        val ids = listOf("id1", "id2")
        val expectedList = listOf(MyLibrary().apply { id = "id1" })
        coEvery { myLibraryDao.getByUnderscoreIds(ids) } returns expectedList

        val result = repository.getLibraryItemsByIds(ids)

        assertEquals(expectedList, result)
        coVerify(exactly = 1) { myLibraryDao.getByUnderscoreIds(ids) }
    }

    @Test
    fun `getLibraryItemsByLocalAddress returns items from dao`() = runTest {
        val address = "local/address"
        val expectedList = listOf(MyLibrary().apply { id = "id1" })
        coEvery { myLibraryDao.getByLocalAddress(address) } returns expectedList

        val result = repository.getLibraryItemsByLocalAddress(address)

        assertEquals(expectedList, result)
        coVerify(exactly = 1) { myLibraryDao.getByLocalAddress(address) }
    }

    @Test
    fun `getLibraryListForUser returns empty list if userId is null`() = runTest {
        val result = repository.getLibraryListForUser(null)

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { myLibraryDao.getPublicNeedingUpdateForUserPattern(any()) }
    }

    @Test
    fun `getLibraryListForUser returns items needing update from dao`() = runTest {
        val userId = "user123"
        val needsUpdateLib = MyLibrary().apply { resourceOffline = false }

        coEvery { myLibraryDao.getPublicNeedingUpdateForUserPattern(any()) } returns listOf(needsUpdateLib)

        val result = repository.getLibraryListForUser(userId)

        assertEquals(1, result.size)
        assertEquals(needsUpdateLib, result[0])
        val expectedPattern = "%\"user123\"%"
        coVerify(exactly = 1) { myLibraryDao.getPublicNeedingUpdateForUserPattern(expectedPattern) }
    }

    @Test
    fun `countLibrariesNeedingUpdate returns 0 if userId is null`() = runTest {
        assertEquals(0, repository.countLibrariesNeedingUpdate(null))
        coVerify(exactly = 0) { myLibraryDao.countPublicNeedingUpdateForUserPattern(any()) }
    }

    @Test
    fun `countLibrariesNeedingUpdate delegates to dao`() = runTest {
        val userId = "user123"
        val expectedPattern = "%\"user123\"%"
        coEvery { myLibraryDao.countPublicNeedingUpdateForUserPattern(expectedPattern) } returns 3

        val count = repository.countLibrariesNeedingUpdate(userId)

        assertEquals(3, count)
        coVerify(exactly = 1) { myLibraryDao.countPublicNeedingUpdateForUserPattern(expectedPattern) }
    }

    @Test
    fun `getAllLibrariesToSync delegates directly to getSyncable`() = runTest {
        val syncableList = listOf(MyLibrary().apply { id = "s1" })
        coEvery { myLibraryDao.getSyncable() } returns syncableList

        val result = repository.getAllLibrariesToSync()

        assertEquals(syncableList, result)
        coVerify(exactly = 1) { myLibraryDao.getSyncable() }
    }

    @Test
    fun `getDownloadSuggestionList uses user pattern when target user is available`() = runTest {
        val userLib = MyLibrary().apply { id = "ul1" }
        val expectedPattern = "%\"user123\"%"
        coEvery { myLibraryDao.getPublicNeedingUpdateForUserPattern(expectedPattern) } returns listOf(userLib)

        val result = repository.getDownloadSuggestionList("user123")

        assertEquals(listOf(userLib), result)
        coVerify(exactly = 1) { myLibraryDao.getPublicNeedingUpdateForUserPattern(expectedPattern) }
        coVerify(exactly = 0) { myLibraryDao.getPublicNeedingUpdate() }
    }

    @Test
    fun `getDownloadSuggestionList falls back to public needing update when user pattern yields empty`() = runTest {
        val publicLib = MyLibrary().apply { id = "pl1" }
        val expectedPattern = "%\"user123\"%"
        coEvery { myLibraryDao.getPublicNeedingUpdateForUserPattern(expectedPattern) } returns emptyList()
        coEvery { myLibraryDao.getPublicNeedingUpdate() } returns listOf(publicLib)

        val result = repository.getDownloadSuggestionList("user123")

        assertEquals(listOf(publicLib), result)
        coVerify(exactly = 1) { myLibraryDao.getPublicNeedingUpdateForUserPattern(expectedPattern) }
        coVerify(exactly = 1) { myLibraryDao.getPublicNeedingUpdate() }
    }

    @Test
    fun `getMyLibrary returns empty list if userId is null or blank`() = runTest {
        assertTrue(repository.getMyLibrary(null).isEmpty())
        assertTrue(repository.getMyLibrary("").isEmpty())
        assertTrue(repository.getMyLibrary("   ").isEmpty())

        coVerify(exactly = 0) { myLibraryDao.getForUserPattern(any()) }
    }

    @Test
    fun `getMyLibrary returns items from dao`() = runTest {
        val userId = "user123"
        val expectedList = listOf(MyLibrary().apply { id = "id1" })
        coEvery { myLibraryDao.getForUserPattern(any()) } returns expectedList

        val result = repository.getMyLibrary(userId)

        assertEquals(expectedList, result)
        val expectedPattern = "%\"user123\"%"
        coVerify(exactly = 1) { myLibraryDao.getForUserPattern(expectedPattern) }
    }

    @Test
    fun `getAllStepResources returns empty list if stepId is null`() = runTest {
        val result = repository.getAllStepResources(null)

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { myLibraryDao.getByStepId(any()) }
    }

    @Test
    fun `getAllStepResources returns items from dao`() = runTest {
        val stepId = "step123"
        val expectedList = listOf(MyLibrary().apply { id = "id1" })
        coEvery { myLibraryDao.getByStepId(stepId) } returns expectedList

        val result = repository.getAllStepResources(stepId)

        assertEquals(expectedList, result)
        coVerify(exactly = 1) { myLibraryDao.getByStepId(stepId) }
    }

    @Test
    fun `batchInsertResources avoids N plus one queries`() = runTest {
        val documents = (1..5).map {
            val doc = com.google.gson.JsonObject()
            doc.addProperty("_id", "id_$it")
            doc.addProperty("_rev", "1-abc")
            doc.addProperty("title", "Title $it")
            doc
        }

        coEvery { myLibraryDao.getByIds(any()) } returns emptyList()
        coEvery { myLibraryDao.upsertAll(any()) } returns Unit

        repository.batchInsertResources(documents)

        coVerify(exactly = 1) { myLibraryDao.upsertAll(match { it.size == 5 }) }
        coVerify(exactly = 0) { myLibraryDao.upsert(any()) }
    }

    @Test
    fun `batchInsertMyLibrary avoids N plus one queries`() = runTest {
        val documents = (1..5).map {
            val doc = com.google.gson.JsonObject()
            doc.addProperty("_id", "id_$it")
            doc.addProperty("_rev", "1-abc")
            doc.addProperty("title", "Title $it")
            doc
        }

        coEvery { myLibraryDao.getByIds(any()) } returns emptyList()
        coEvery { myLibraryDao.upsertAll(any()) } returns Unit

        repository.batchInsertMyLibrary("shelfUserA", documents)

        coVerify(exactly = 1) { myLibraryDao.upsertAll(match { it.size == 5 }) }
        coVerify(exactly = 0) { myLibraryDao.upsert(any()) }
    }

    @Test
    fun `removeResourcesFromShelf batches dao calls instead of one per item`() = runTest {
        val userId = "testUser123"
        val resourceIds = (1..50).map { "resource$it" }
        val libraryItems = resourceIds.map { rid -> MyLibrary().apply { resourceId = rid; setUserId(userId) } }

        coEvery { myLibraryDao.getByResourceIds(resourceIds) } returns libraryItems
        coEvery { myLibraryDao.upsertAll(any()) } returns Unit
        coEvery { removedLogDao.insertAll(any()) } returns Unit

        val result = repository.removeResourcesFromShelf(resourceIds, userId)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { myLibraryDao.getByResourceIds(resourceIds) }
        coVerify(exactly = 1) { myLibraryDao.upsertAll(match { it.size == 50 }) }
        coVerify(exactly = 1) { removedLogDao.insertAll(match { it.size == 50 }) }
        coVerify(exactly = 0) { myLibraryDao.upsert(any()) }
    }

    @Test
    fun `removeResourcesFromShelf with empty list does nothing`() = runTest {
        val result = repository.removeResourcesFromShelf(emptyList(), "testUser123")

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { myLibraryDao.getByResourceIds(any()) }
        coVerify(exactly = 0) { removedLogDao.insertAll(any()) }
    }

    @Test
    fun `getMyLibIds calls getIdsForUserPattern and returns JsonArray of ids`() = runTest {
        val userId = "user123"
        val expectedPattern = "%\"user123\"%"
        val expectedIds = listOf("id1", "id2")
        coEvery { myLibraryDao.getIdsForUserPattern(expectedPattern) } returns expectedIds

        val result = repository.getMyLibIds(userId)

        coVerify(exactly = 1) { myLibraryDao.getIdsForUserPattern(expectedPattern) }
        assertEquals(2, result.size())
        assertEquals("id1", result.get(0).asString)
        assertEquals("id2", result.get(1).asString)
    }

    @Test
    fun `getResourceTitlesMap calls getResourceTitles and returns mapped titles`() = runTest {
        val projections = listOf(
            ResourceTitleProjection("res1", "Title 1"),
            ResourceTitleProjection("res2", "Title 2")
        )
        coEvery { myLibraryDao.getResourceTitles() } returns projections

        val result = repository.getResourceTitlesMap()

        coVerify(exactly = 1) { myLibraryDao.getResourceTitles() }
        assertEquals(2, result.size)
        assertEquals("Title 1", result["res1"])
        assertEquals("Title 2", result["res2"])
    }

    @Test
    fun `markResourceUploaded calls createLocalResourceLink when resource is private`() = runTest {
        val localId = "local1"
        val remoteId = "remote1"
        val remoteRev = "1-rev"
        val teamId = "team123"
        val library = MyLibrary().apply {
            id = localId
            title = "Private Resource"
            isPrivate = true
            privateFor = teamId
        }

        val mockTeamsRepository = mockk<TeamsRepository>(relaxed = true)
        every { teamsRepositoryLazy.get() } returns mockTeamsRepository
        coEvery { myLibraryDao.getById(localId) } returns library
        coEvery { myLibraryDao.upsert(any()) } returns Unit

        val result = repository.markResourceUploaded(localId, remoteId, remoteRev, "planet1")

        assertTrue(result)
        coVerify { myLibraryDao.upsert(match { it._id == remoteId && it._rev == remoteRev }) }
        coVerify {
            mockTeamsRepository.createLocalResourceLink(
                teamId = teamId,
                resourceId = remoteId,
                title = "Private Resource",
                planetCode = "planet1"
            )
        }
    }

    @Test
    fun `downloadFiles returns provided list directly when not null`() = runTest {
        val scope = CoroutineScope(SupervisorJob())
        mockkObject(MainApplication)
        mockkObject(DownloadUtils)
        try {
            every { MainApplication.applicationScope } returns scope
            coEvery { configurationsRepository.checkServerAvailability() } returns false
            every { DownloadUtils.downloadAllFiles(any()) } returns arrayListOf("url1")

            val library = MyLibrary().apply { _id = "lib1"; resourceId = "r1" }
            val provided = listOf(library)

            val result = repository.downloadFiles(provided)

            assertEquals(1, result.size)
            assertSame(library, result[0])
            coVerify(exactly = 0) { myLibraryDao.getSyncable() }
            verify(exactly = 1) { DownloadUtils.downloadAllFiles(provided) }
        } finally {
            scope.cancel()
            unmockkObject(MainApplication)
            unmockkObject(DownloadUtils)
        }
    }

    @Test
    fun `downloadFiles falls back to getAllLibrariesToSync when list is null`() = runTest {
        val scope = CoroutineScope(SupervisorJob())
        mockkObject(MainApplication)
        mockkObject(DownloadUtils)
        try {
            every { MainApplication.applicationScope } returns scope
            coEvery { configurationsRepository.checkServerAvailability() } returns false
            val synced = listOf(MyLibrary().apply { _id = "synced1" })
            coEvery { myLibraryDao.getSyncable() } returns synced
            every { DownloadUtils.downloadAllFiles(any()) } returns arrayListOf("url2")

            val result = repository.downloadFiles(null)

            assertEquals(1, result.size)
            assertEquals("synced1", result[0]._id)
            coVerify(exactly = 1) { myLibraryDao.getSyncable() }
        } finally {
            scope.cancel()
            unmockkObject(MainApplication)
            unmockkObject(DownloadUtils)
        }
    }

    @Test
    fun `saveLocalResource copies the source file into the ole directory and stores the bare filename`() = runTest {
        val sourceFolder = temporaryFolder.newFolder("source")
        val externalFilesDir = temporaryFolder.newFolder("external")
        val sourceFile = File(sourceFolder, "report.pdf").apply { writeText("content") }

        every { dispatcherProvider.io } returns testDispatcher
        coEvery { myLibraryDao.countByTitle("My Report") } returns 0
        val savedSlot = slot<MyLibrary>()
        coEvery { myLibraryDao.upsert(capture(savedSlot)) } returns Unit

        mockkObject(FileUtils)
        every { FileUtils.getExternalFilesDir(context) } returns externalFilesDir
        every { FileUtils.getLibraryFile(externalFilesDir, any(), "report.pdf") } answers {
            File(externalFilesDir, "ole/${secondArg<String>()}/report.pdf")
        }

        try {
            val result = repository.saveLocalResource(localResourceRequest(sourceFile.absolutePath))

            assertTrue(result.isSuccess)
            val saved = savedSlot.captured
            assertEquals("report.pdf", saved.resourceLocalAddress)
            assertEquals("report.pdf", saved.filename)
            val destinationFile = File(externalFilesDir, "ole/${saved.id}/report.pdf")
            assertTrue(destinationFile.exists())
            assertEquals("content", destinationFile.readText())
        } finally {
            unmockkObject(FileUtils)
        }
    }

    @Test
    fun `saveLocalResource fails when the source file does not exist`() = runTest {
        val missingFile = File(temporaryFolder.root, "missing.pdf")
        coEvery { myLibraryDao.countByTitle("My Report") } returns 0

        val result = repository.saveLocalResource(localResourceRequest(missingFile.absolutePath))

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { myLibraryDao.upsert(any()) }
    }

    private fun localResourceRequest(resourceUrl: String?): LocalResourceRequest {
        return LocalResourceRequest(
            title = "My Report",
            addedBy = "tester",
            author = null,
            year = null,
            description = null,
            publisher = null,
            linkToLicense = null,
            openWith = null,
            language = null,
            mediaType = null,
            resourceType = null,
            subjects = null,
            levels = null,
            resourceFor = null,
            resourceUrl = resourceUrl,
            userId = "user-1",
            isPrivateTeamResource = false,
            teamId = null
        )
    }
}
