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
import org.ole.planet.myplanet.repository.UploadQueryContract
import org.ole.planet.myplanet.services.upload.UploadConfig
import org.ole.planet.myplanet.services.upload.UploadSerializer

open class DummyModel : RealmObject()

@OptIn(ExperimentalCoroutinesApi::class)
class UploadRepositoryImplTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var repository: UploadRepositoryImpl
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        databaseService = mockk(relaxed = true)
        repository = UploadRepositoryImpl(databaseService, testDispatcher)
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

        val config = UploadQueryContract(
            modelClass = DummyModel::class,
            queryBuilder = queryBuilder
        )

        every { filteredQuery.findAll() } returns results
        every { realm.copyFromRealm(results) } returns expectedList

        val actualList = repository.queryPending(config)

        assertEquals(expectedList, actualList)
        verify(exactly = 1) { realm.copyFromRealm(results) }
    }
}
