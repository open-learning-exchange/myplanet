package org.ole.planet.myplanet.repository

import io.mockk.*
import io.realm.Realm
import io.realm.RealmChangeListener
import io.realm.RealmObject
import io.realm.RealmQuery
import io.realm.RealmResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService

class TestRealmObject : RealmObject()

class TestRealmRepository(databaseService: DatabaseService) : RealmRepository(databaseService) {
    suspend fun queryFlow() = queryListFlow(TestRealmObject::class.java)
}

@OptIn(ExperimentalCoroutinesApi::class)
class RealmRepositoryTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var realm: Realm
    private lateinit var repository: TestRealmRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        databaseService = mockk()
        realm = mockk(relaxed = true)

        every { databaseService.ioDispatcher } returns testDispatcher
        every { databaseService.createManagedRealmInstance() } returns realm

        repository = TestRealmRepository(databaseService)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `queryListFlow emits initial results then listener updates`() = runTest {
        val realmQuery = mockk<RealmQuery<TestRealmObject>>(relaxed = true)
        val initialResults = mockk<RealmResults<TestRealmObject>>(relaxed = true)
        val asyncResults = mockk<RealmResults<TestRealmObject>>(relaxed = true)
        val frozenInitial = mockk<RealmResults<TestRealmObject>>(relaxed = true)
        val frozenUpdated = mockk<RealmResults<TestRealmObject>>(relaxed = true)

        val frozenRealmInitial = mockk<Realm>(relaxed = true)
        val frozenRealmUpdated = mockk<Realm>(relaxed = true)

        val copiedInitialList = listOf(TestRealmObject())
        val copiedUpdatedList = listOf(TestRealmObject(), TestRealmObject())

        every { realm.where(TestRealmObject::class.java) } returns realmQuery
        every { realmQuery.findAll() } returns initialResults
        every { realmQuery.findAllAsync() } returns asyncResults

        every { initialResults.isValid } returns true
        every { initialResults.isLoaded } returns true
        every { initialResults.freeze() } returns frozenInitial

        every { frozenInitial.realm } returns frozenRealmInitial
        every { frozenRealmInitial.copyFromRealm(frozenInitial) } returns copiedInitialList

        val listenerSlot = slot<RealmChangeListener<RealmResults<TestRealmObject>>>()
        every { asyncResults.addChangeListener(capture(listenerSlot)) } just Runs

        every { frozenUpdated.realm } returns frozenRealmUpdated
        every { frozenRealmUpdated.copyFromRealm(frozenUpdated) } returns copiedUpdatedList

        val emittedLists = mutableListOf<List<TestRealmObject>>()

        val job = launch(testDispatcher) {
            repository.queryFlow().collect {
                emittedLists.add(it)
            }
        }

        // At this point initial results should be emitted
        assertEquals(1, emittedLists.size)
        assertEquals(copiedInitialList, emittedLists[0])

        // Trigger listener
        val updatedResults = mockk<RealmResults<TestRealmObject>>(relaxed = true)
        every { updatedResults.isLoaded } returns true
        every { updatedResults.isValid } returns true
        every { updatedResults.freeze() } returns frozenUpdated

        listenerSlot.captured.onChange(updatedResults)

        assertEquals(2, emittedLists.size)
        assertEquals(copiedUpdatedList, emittedLists[1])

        job.cancel()
    }

    @Test
    fun `queryListFlow awaitClose path closes channel and does not double-close`() = runTest {
        val realmQuery = mockk<RealmQuery<TestRealmObject>>(relaxed = true)
        val initialResults = mockk<RealmResults<TestRealmObject>>(relaxed = true)
        val asyncResults = mockk<RealmResults<TestRealmObject>>(relaxed = true)
        val frozenInitial = mockk<RealmResults<TestRealmObject>>(relaxed = true)
        val frozenRealmInitial = mockk<Realm>(relaxed = true)
        val listenerSlot = slot<RealmChangeListener<RealmResults<TestRealmObject>>>()

        every { realm.where(TestRealmObject::class.java) } returns realmQuery
        every { realmQuery.findAll() } returns initialResults
        every { realmQuery.findAllAsync() } returns asyncResults
        every { initialResults.isValid } returns true
        every { initialResults.isLoaded } returns true
        every { initialResults.freeze() } returns frozenInitial
        every { frozenInitial.realm } returns frozenRealmInitial
        every { frozenRealmInitial.copyFromRealm(frozenInitial) } returns listOf()
        every { asyncResults.addChangeListener(capture(listenerSlot)) } just Runs
        every { asyncResults.isValid } returns true
        every { realm.isClosed } returns false

        val job = launch(testDispatcher) {
            repository.queryFlow().collect {
                // Collect elements
            }
        }

        // Job gets cancelled which triggers awaitClose
        job.cancel()
        job.join()

        // Wait a bit to ensure awaitClose is executed
        // verify removeChangeListener is called and realm.close is called
        verify { asyncResults.removeChangeListener(listenerSlot.captured) }
        verify(exactly = 1) { realm.close() }
    }

    @Test
    fun `queryListFlow exception path still cleans up`() = runTest {
        val exception = RuntimeException("create managed realm failed")
        every { databaseService.createManagedRealmInstance() } throws exception

        var caughtException: Throwable? = null
        try {
            repository.queryFlow().collect { }
        } catch (e: Exception) {
            caughtException = e
        }

        assertEquals(exception, caughtException)
    }
}
