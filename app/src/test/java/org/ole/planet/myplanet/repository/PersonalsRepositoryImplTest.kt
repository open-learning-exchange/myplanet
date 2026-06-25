package org.ole.planet.myplanet.repository

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmMyPersonal

@OptIn(ExperimentalCoroutinesApi::class)
class PersonalsRepositoryImplTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var mockRealm: Realm
    private lateinit var repository: PersonalsRepositoryImpl
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Logger.getLogger("io.mockk").level = Level.OFF
        mockkStatic(io.realm.log.RealmLog::class)
        every { io.realm.log.RealmLog.error(any<Throwable>(), any<String>(), *anyVararg()) } just Runs
        every { io.realm.log.RealmLog.error(any<String>(), *anyVararg()) } just Runs

        databaseService = mockk(relaxed = true)
        mockRealm = mockk(relaxed = true)

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val transaction = firstArg<(Realm) -> Unit>()
            transaction(mockRealm)
        }

        coEvery { databaseService.withRealmAsync<Any>(any()) } answers {
            val operation = firstArg<(Realm) -> Any>()
            operation(mockRealm)
        }

        every { databaseService.createManagedRealmInstance() } returns mockRealm
        every { databaseService.ioDispatcher } returns testDispatcher

        val apiInterface = mockk<org.ole.planet.myplanet.data.api.ApiInterface>(relaxed = true)
        val context = mockk<android.content.Context>(relaxed = true)
        repository = PersonalsRepositoryImpl(databaseService, testDispatcher, apiInterface, context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun mockQueryResults(vararg results: List<RealmMyPersonal>): RealmQuery<RealmMyPersonal> {
        val mockQuery = mockk<RealmQuery<RealmMyPersonal>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmMyPersonal>>(relaxed = true)

        every { mockRealm.where(RealmMyPersonal::class.java) } returns mockQuery

        // Setup fluent return
        every { mockQuery.equalTo(any<String>(), any<String>()) } returns mockQuery
        every { mockQuery.equalTo(any<String>(), any<String>(), any()) } returns mockQuery
        every { mockQuery.equalTo(any<String>(), any<Boolean>()) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        every { mockQuery.count() } returns (results.firstOrNull()?.size?.toLong() ?: 0L)

        // Map sequential calls to copyFromRealm to different results
        if (results.size == 1) {
             every { mockRealm.copyFromRealm(mockResults) } returns results[0]
        } else if (results.isNotEmpty()) {
             every { mockRealm.copyFromRealm(mockResults) } returnsMany results.toList()
        }

        return mockQuery
    }

    @Test
    fun `personalTitleExists returns true when title and user match`() = runTest {
        val mockQuery = mockQueryResults(listOf(RealmMyPersonal()))
        val result = repository.personalTitleExists("My Title", "user1")
        assertTrue(result)
        verify {
            mockQuery.equalTo("title", "My Title", io.realm.Case.INSENSITIVE)
            mockQuery.equalTo("userId", "user1")
        }
    }

    @Test
    fun `personalTitleExists returns false when title does not exist`() = runTest {
        val mockQuery = mockQueryResults(emptyList())
        val result = repository.personalTitleExists("Missing", null)
        assertFalse(result)
        verify {
            mockQuery.equalTo("title", "Missing", io.realm.Case.INSENSITIVE)
        }
        verify(exactly = 0) {
             mockQuery.equalTo("userId", any<String>())
        }
    }

    @Test
    fun `savePersonalResource sets id and properties before saving`() = runTest {
        val savedObjectSlot = slot<RealmMyPersonal>()
        every { mockRealm.copyToRealmOrUpdate(capture(savedObjectSlot)) } returns mockk()

        repository.savePersonalResource(
            title = "Test Title",
            userId = "user1",
            userName = "Test User",
            path = "/path/to/file",
            description = "Test Desc"
        )

        verify { mockRealm.copyToRealmOrUpdate(any<RealmMyPersonal>()) }

        val captured = savedObjectSlot.captured
        assertEquals("Test Title", captured.title)
        assertEquals("user1", captured.userId)
        assertEquals("Test User", captured.userName)
        assertEquals("/path/to/file", captured.path)
        assertEquals("Test Desc", captured.description)
        assertTrue(captured.id != null)
        assertEquals(captured.id, captured._id)
    }

    @Test
    fun `getPersonalResources returns empty flow for null or blank userId`() = runTest {
        val resultNull = repository.getPersonalResources(null).first()
        assertTrue(resultNull.isEmpty())

        val resultBlank = repository.getPersonalResources("   ").first()
        assertTrue(resultBlank.isEmpty())
    }

    @Test
    fun `getPersonalResources returns flow of personals for valid userId`() = runTest {
        val mockQuery = mockk<RealmQuery<RealmMyPersonal>>(relaxed = true)
        val initialResults = mockk<RealmResults<RealmMyPersonal>>(relaxed = true)
        val frozenInitial = mockk<RealmResults<RealmMyPersonal>>(relaxed = true)
        val frozenRealmInitial = mockk<Realm>(relaxed = true)
        val expectedList = listOf(RealmMyPersonal())

        every { mockRealm.where(RealmMyPersonal::class.java) } returns mockQuery
        every { mockQuery.equalTo("userId", "user1") } returns mockQuery
        every { mockQuery.findAll() } returns initialResults

        every { initialResults.isValid } returns true
        every { initialResults.isLoaded } returns true
        every { initialResults.freeze() } returns frozenInitial
        every { frozenInitial.realm } returns frozenRealmInitial
        every { frozenRealmInitial.copyFromRealm(frozenInitial) } returns expectedList

        val result = repository.getPersonalResources("user1").first()
        assertEquals(expectedList, result)

        verify { mockQuery.equalTo("userId", "user1") }
    }

    @Test
    fun `deletePersonalResource deletes both _id and id`() = runTest {
        val mockQuery = mockQueryResults(listOf(RealmMyPersonal()))
        val mockPersonal = mockk<RealmMyPersonal>(relaxed = true)
        every { mockQuery.findFirst() } returns mockPersonal

        repository.deletePersonalResource("test-id")

        verify {
            mockQuery.equalTo("_id", "test-id")
            mockQuery.equalTo("id", "test-id")
            mockPersonal.deleteFromRealm()
        }
    }

    @Test
    fun `updatePersonalResource calls updater on matched _id and id`() = runTest {
        val mockQuery = mockQueryResults()
        val mockPersonal_Id = RealmMyPersonal().apply { title = "Old" }
        val mockPersonalId = RealmMyPersonal().apply { title = "Old" }

        every { mockQuery.findFirst() } returnsMany listOf(mockPersonal_Id, mockPersonalId)

        var updateCount = 0
        repository.updatePersonalResource("test-id") { personal ->
            personal.title = "New Title"
            updateCount++
        }

        assertEquals(2, updateCount)
        assertEquals("New Title", mockPersonalId.title)
        assertEquals("New Title", mockPersonal_Id.title)
    }

    @Test
    fun `getPendingPersonalUploads queries correctly`() = runTest {
        val mockQuery = mockQueryResults(listOf(RealmMyPersonal(), RealmMyPersonal()))
        val results = repository.getPendingPersonalUploads("user1")

        assertEquals(2, results.size)
        verify {
            mockQuery.equalTo("userId", "user1")
            mockQuery.equalTo("isUploaded", false)
        }
    }

    @Test
    fun `updatePersonalAfterSync updates fields properly`() = runTest {
        val mockQuery = mockQueryResults()
        val mockPersonal = RealmMyPersonal()
        every { mockQuery.equalTo("id", "test-id").findFirst() } returns mockPersonal

        repository.updatePersonalAfterSync("test-id", "new-id", "rev-1")

        assertTrue(mockPersonal.isUploaded)
        assertEquals("new-id", mockPersonal._id)
        assertEquals("rev-1", mockPersonal._rev)
    }
}
