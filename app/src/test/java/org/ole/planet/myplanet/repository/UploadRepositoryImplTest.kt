package org.ole.planet.myplanet.repository

import io.mockk.coEvery
import io.mockk.every
import io.mockk.invoke
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmObject
import io.realm.RealmQuery
import io.realm.RealmResults
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.services.upload.UploadConfig
import org.ole.planet.myplanet.services.upload.UploadSerializer

import org.ole.planet.myplanet.data.api.ApiInterface
import io.mockk.mockkStatic
import org.ole.planet.myplanet.utils.UrlUtils

open class DummyModel : RealmObject()

@OptIn(ExperimentalCoroutinesApi::class)
class UploadRepositoryImplTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var apiInterface: ApiInterface
    private lateinit var repository: UploadRepositoryImpl
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        databaseService = mockk(relaxed = true)
        apiInterface = mockk(relaxed = true)
        repository = UploadRepositoryImpl(databaseService, apiInterface, testDispatcher)

        val spm = mockk<org.ole.planet.myplanet.services.SharedPrefManager>(relaxed = true)
        every { spm.getUrlUser() } returns "user"
        every { spm.getUrlPwd() } returns "pass"
        UrlUtils.init(spm)
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } returns "encoded_credentials"
    }

    @After
    fun tearDown() {
        io.mockk.unmockkAll()
    }


    @Test
    fun `queryPending returns list from copyFromRealm`() = runTest {
        val realm = mockk<Realm>(relaxed = true)
        val querySlot = slot<(Realm) -> Any>()

        coEvery { databaseService.withRealmAsync(capture(querySlot)) } answers {
            querySlot.captured.invoke(realm)
        }

        val realmQuery = mockk<RealmQuery<DummyModel>>(relaxed = true)
        val filteredQuery = mockk<RealmQuery<DummyModel>>(relaxed = true)
        val results = mockk<RealmResults<DummyModel>>(relaxed = true)
        val expectedList = listOf(DummyModel(), DummyModel())

        every { realm.where(DummyModel::class.java) } returns realmQuery

        val queryBuilder: (RealmQuery<DummyModel>) -> RealmQuery<DummyModel> = { q ->
            assertEquals(realmQuery, q)
            filteredQuery
        }

        val config = UploadConfig(
            modelClass = DummyModel::class,
            endpoint = "test",
            queryBuilder = queryBuilder,
            serializer = mockk<UploadSerializer<DummyModel>>(),
            idExtractor = { null }
        )

        every { filteredQuery.findAll() } returns results
        every { realm.copyFromRealm(results) } returns expectedList

        val actualList = repository.queryPending(config)

        assertEquals(expectedList, actualList)
        verify(exactly = 1) { realm.copyFromRealm(results) }
    }


    @Test
    fun `postUpload calls postDoc on ApiInterface`() = runTest {
        val url = "testUrl"
        val data = com.google.gson.JsonObject()
        val expectedResponse = mockk<retrofit2.Response<com.google.gson.JsonObject>>()
        val spm = mockk<org.ole.planet.myplanet.services.SharedPrefManager>(relaxed = true)
        every { spm.getUrlUser() } returns "user"
        every { spm.getUrlPwd() } returns "pass"
        UrlUtils.init(spm)
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } returns "encoded_credentials"

        coEvery { apiInterface.postDoc(any(), eq("application/json"), eq(url), eq(data)) } returns expectedResponse

        val result = repository.postUpload(url, data)

        assertEquals(expectedResponse, result)
        io.mockk.coVerify(exactly = 1) { apiInterface.postDoc(any(), eq("application/json"), eq(url), eq(data)) }
    }

    @Test
    fun `putUpload calls putDoc on ApiInterface`() = runTest {
        val url = "testUrl"
        val data = com.google.gson.JsonObject()
        val expectedResponse = mockk<retrofit2.Response<com.google.gson.JsonObject>>()
        val spm = mockk<org.ole.planet.myplanet.services.SharedPrefManager>(relaxed = true)
        every { spm.getUrlUser() } returns "user"
        every { spm.getUrlPwd() } returns "pass"
        UrlUtils.init(spm)
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } returns "encoded_credentials"

        coEvery { apiInterface.putDoc(any(), eq("application/json"), eq(url), eq(data)) } returns expectedResponse

        val result = repository.putUpload(url, data)

        assertEquals(expectedResponse, result)
        io.mockk.coVerify(exactly = 1) { apiInterface.putDoc(any(), eq("application/json"), eq(url), eq(data)) }
    }

    @Test
    fun `fetchExistingDoc calls getJsonObject on ApiInterface`() = runTest {
        val url = "testUrl"
        val expectedResponse = mockk<retrofit2.Response<com.google.gson.JsonObject>>()
        val spm = mockk<org.ole.planet.myplanet.services.SharedPrefManager>(relaxed = true)
        every { spm.getUrlUser() } returns "user"
        every { spm.getUrlPwd() } returns "pass"
        UrlUtils.init(spm)
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } returns "encoded_credentials"

        coEvery { apiInterface.getJsonObject(any(), eq(url)) } returns expectedResponse

        val result = repository.fetchExistingDoc(url)

        assertEquals(expectedResponse, result)
        io.mockk.coVerify(exactly = 1) { apiInterface.getJsonObject(any(), eq(url)) }
    }
}
