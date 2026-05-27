package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.*
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import io.realm.Sort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
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
    private lateinit var mockRealm: Realm
    private lateinit var repository: CommunityRepositoryImpl
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        databaseService = mockk(relaxed = true)
        apiInterface = mockk(relaxed = true)
        mockRealm = mockk(relaxed = true)

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val operation = firstArg<(Realm) -> Unit>()
            operation(mockRealm)
        }

        coEvery { databaseService.withRealmAsync<List<RealmCommunity>>(any()) } answers {
            val operation = firstArg<(Realm) -> List<RealmCommunity>>()
            operation(mockRealm)
        }

        repository = CommunityRepositoryImpl(databaseService, testDispatcher, apiInterface)
    }

    @Test
    fun `replaceAll clears existing communities and inserts new ones`() = runTest {
        val rows = JsonArray()
        val row1 = JsonObject()
        val doc1 = JsonObject()
        doc1.addProperty("_id", "id1")
        doc1.addProperty("name", "learning")
        doc1.addProperty("localDomain", "local1")
        doc1.addProperty("parentDomain", "parent1")
        doc1.addProperty("registrationRequest", "req1")
        row1.add("doc", doc1)
        rows.add(row1)

        val row2 = JsonObject()
        val doc2 = JsonObject()
        doc2.addProperty("_id", "id2")
        doc2.addProperty("name", "other")
        doc2.addProperty("localDomain", "local2")
        doc2.addProperty("parentDomain", "parent2")
        doc2.addProperty("registrationRequest", "req2")
        row2.add("doc", doc2)
        rows.add(row2)

        repository.replaceAll(rows)

        verify { mockRealm.delete(RealmCommunity::class.java) }
        verify { mockRealm.insertOrUpdate(any<List<RealmCommunity>>()) }
    }

    @Test
    fun `getAllSorted returns list sorted by weight`() = runTest {
        val mockQuery = mockk<RealmQuery<RealmCommunity>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmCommunity>>(relaxed = true)
        val expectedList = listOf(RealmCommunity())

        every { mockRealm.where(RealmCommunity::class.java) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        every { mockRealm.copyFromRealm(mockResults) } returns expectedList

        val result = repository.getAllSorted()

        verify { mockQuery.sort("weight", Sort.ASCENDING) }
        assertEquals(expectedList, result)
    }

    @Test
    fun `syncCommunityDocs returns true on successful API response`() = runTest {
        val jsonResponse = JsonObject()
        jsonResponse.add("rows", JsonArray())
        val response = Response.success(jsonResponse)
        coEvery { apiInterface.getJsonObject(any(), any()) } returns response

        val result = repository.syncCommunityDocs()

        assertTrue(result)
        coVerify { apiInterface.getJsonObject("", "https://planet.earth.ole.org/db/communityregistrationrequests/_all_docs?include_docs=true") }
    }

    @Test
    fun `syncCommunityDocs returns false on unsuccessful API response`() = runTest {
        val response = Response.error<JsonObject>(400, "Error".toResponseBody("application/json".toMediaTypeOrNull()))
        coEvery { apiInterface.getJsonObject(any(), any()) } returns response

        val result = repository.syncCommunityDocs()

        assertFalse(result)
    }

    @Test
    fun `syncCommunityDocs returns false on exception`() = runTest {
        coEvery { apiInterface.getJsonObject(any(), any()) } answers { throw RuntimeException("Network Error") }

        val result = repository.syncCommunityDocs()

        assertFalse(result)
    }

    @Test
    fun `bulkInsertFromSync correctly inserts docs into realm`() {
        val rows = JsonArray()
        val row1 = JsonObject()
        val doc1 = JsonObject()
        doc1.addProperty("_id", "id1")
        row1.add("doc", doc1)
        rows.add(row1)

        val row2 = JsonObject()
        val doc2 = JsonObject()
        doc2.addProperty("_id", "_design/doc2")
        row2.add("doc", doc2)
        rows.add(row2)

        mockkObject(RealmMeetup)
        every { RealmMeetup.insert(any<Realm>(), any<JsonObject>()) } just runs

        repository.bulkInsertFromSync(mockRealm, rows)

        verify(exactly = 1) { RealmMeetup.insert(mockRealm, doc1) }
        verify(exactly = 0) { RealmMeetup.insert(mockRealm, doc2) }

        unmockkObject(RealmMeetup)
    }
}
