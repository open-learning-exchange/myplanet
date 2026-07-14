package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import io.realm.Sort
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.model.RealmCommunity
import org.ole.planet.myplanet.model.RealmMeetup
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityRepositoryImplTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var apiInterface: ApiInterface
    private lateinit var repository: CommunityRepositoryImpl
    private lateinit var mockRealm: Realm

    @Before
    fun setup() {
        Logger.getLogger("io.mockk").level = Level.OFF
        mockRealm = mockk(relaxed = true)
        databaseService = mockk(relaxed = true)
        apiInterface = mockk(relaxed = true)

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val transaction = firstArg<(Realm) -> Unit>()
            transaction(mockRealm)
        }

        coEvery { databaseService.withRealmAsync<List<RealmCommunity>>(any()) } answers {
            val operation = firstArg<(Realm) -> List<RealmCommunity>>()
            operation(mockRealm)
        }

        repository = CommunityRepositoryImpl(
            databaseService,
            UnconfinedTestDispatcher(),
            apiInterface
        )
    }

    @After
    fun teardown() {
        unmockkObject(RealmMeetup)
    }

    @Test
    fun `replaceAll clears existing communities and inserts new ones`() = runTest {
        val rows = JsonArray()

        val doc1 = JsonObject()
        doc1.addProperty("_id", "id_1")
        doc1.addProperty("name", "learning")
        doc1.addProperty("localDomain", "local_1")
        doc1.addProperty("parentDomain", "parent_1")
        doc1.addProperty("registrationRequest", "req_1")

        val row1 = JsonObject()
        row1.add("doc", doc1)
        rows.add(row1)

        val doc2 = JsonObject()
        doc2.addProperty("_id", "id_2")
        doc2.addProperty("name", "other_community")
        doc2.addProperty("localDomain", "local_2")
        doc2.addProperty("parentDomain", "parent_2")
        doc2.addProperty("registrationRequest", "req_2")

        val row2 = JsonObject()
        row2.add("doc", doc2)
        rows.add(row2)

        val communitiesSlot = slot<List<RealmCommunity>>()
        every { mockRealm.insertOrUpdate(capture(communitiesSlot)) } returns Unit

        repository.replaceAll(rows)

        verify { mockRealm.delete(RealmCommunity::class.java) }
        verify { mockRealm.insertOrUpdate(any<List<RealmCommunity>>()) }

        val inserted = communitiesSlot.captured
        assertEquals(2, inserted.size)

        val community1 = inserted[0]
        assertEquals("id_1", community1.id)
        assertEquals("learning", community1.name)
        assertEquals(0, community1.weight) // Should be 0 for "learning"
        assertEquals("local_1", community1.localDomain)
        assertEquals("parent_1", community1.parentDomain)
        assertEquals("req_1", community1.registrationRequest)

        val community2 = inserted[1]
        assertEquals("id_2", community2.id)
        assertEquals("other_community", community2.name)
        assertEquals(10, community2.weight) // Default weight
        assertEquals("local_2", community2.localDomain)
        assertEquals("parent_2", community2.parentDomain)
        assertEquals("req_2", community2.registrationRequest)
    }

    @Test
    fun `getAllSorted returns list sorted by weight`() = runTest {
        val mockQuery = mockk<RealmQuery<RealmCommunity>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmCommunity>>(relaxed = true)
        val testCommunities = listOf(
            RealmCommunity().apply { id = "1"; weight = 0 },
            RealmCommunity().apply { id = "2"; weight = 10 }
        )

        every { mockRealm.where(RealmCommunity::class.java) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        every { mockRealm.copyFromRealm(mockResults) } returns testCommunities

        val result = repository.getAllSorted()

        verify { mockRealm.where(RealmCommunity::class.java) }
        verify { mockQuery.sort("weight", Sort.ASCENDING) }
        verify { mockQuery.findAll() }
        verify { mockRealm.copyFromRealm(mockResults) }

        assertEquals(testCommunities, result)
    }

    @Test
    fun `syncCommunityDocs returns true on successful api call and syncs`() = runTest {
        val jsonResponse = JsonObject()
        val rows = JsonArray()
        val row = JsonObject()
        val doc = JsonObject()
        doc.addProperty("_id", "sync_id")
        doc.addProperty("name", "sync_name")
        row.add("doc", doc)
        rows.add(row)
        jsonResponse.add("rows", rows)

        coEvery { apiInterface.getJsonObject(any(), any()) } returns Response.success(jsonResponse)

        val communitiesSlot = slot<List<RealmCommunity>>()
        every { mockRealm.insertOrUpdate(capture(communitiesSlot)) } returns Unit

        val result = repository.syncCommunityDocs()

        assertTrue(result)
        verify { mockRealm.delete(RealmCommunity::class.java) }
        verify { mockRealm.insertOrUpdate(any<List<RealmCommunity>>()) }

        val inserted = communitiesSlot.captured
        assertEquals(1, inserted.size)
        assertEquals("sync_id", inserted[0].id)
        assertEquals("sync_name", inserted[0].name)
    }

    @Test
    fun `syncCommunityDocs returns false on api failure`() = runTest {
        val errorResponseBody = "Error".toResponseBody("application/json".toMediaTypeOrNull())
        coEvery { apiInterface.getJsonObject(any(), any()) } returns Response.error(400, errorResponseBody)

        val result = repository.syncCommunityDocs()

        assertFalse(result)
        verify(exactly = 0) { mockRealm.delete(RealmCommunity::class.java) }
        verify(exactly = 0) { mockRealm.insertOrUpdate(any<List<RealmCommunity>>()) }
    }

    @Test
    fun `syncCommunityDocs returns false on exception`() = runTest {
        coEvery { apiInterface.getJsonObject(any(), any()) } throws RuntimeException("Network error")

        val result = repository.syncCommunityDocs()

        assertFalse(result)
        verify(exactly = 0) { mockRealm.delete(RealmCommunity::class.java) }
        verify(exactly = 0) { mockRealm.insertOrUpdate(any<List<RealmCommunity>>()) }
    }

    @Test
    fun `insertMeetupsFromSync calls RealmMeetup insertList`() = runTest {
        mockkObject(RealmMeetup)
        every { RealmMeetup.insertList(any(), any(), any()) } returns Unit

        val docs = listOf(JsonObject(), JsonObject())
        repository.insertMeetupsFromSync(docs)

        verify { RealmMeetup.insertList(mockRealm, "", docs) }
    }
}
