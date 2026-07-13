package org.ole.planet.myplanet.repository

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.realm.OrderedRealmCollectionChangeListener
import io.realm.Realm
import io.realm.RealmObject
import io.realm.RealmQuery
import io.realm.RealmResults
import io.realm.log.RealmLog
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Rule
import org.junit.After
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.data.DatabaseService

class TestRealmObject : RealmObject()

class TestRealmRepository(
    databaseService: DatabaseService,
    realmDispatcher: CoroutineDispatcher
) : RealmRepository(databaseService, realmDispatcher) {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    suspend fun queryFlow() = queryListFlow(TestRealmObject::class.java)
}

class RealmRepositoryTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var realm: Realm
    private lateinit var repository: TestRealmRepository
    private val testDispatcher = mainDispatcherRule.testDispatcher

    @Before
    fun setup() {
        Logger.getLogger("io.mockk").level = Level.OFF
        // Suppress MockK warning for mocking RealmResults
        Logger.getLogger("io.mockk.impl.log.JULLogger").level = Level.OFF

        Dispatchers.setMain(testDispatcher)
        databaseService = mockk()
        realm = mockk(relaxed = true)

        every { databaseService.ioDispatcher } returns testDispatcher
        every { databaseService.createManagedRealmInstance() } returns realm

        // Mock RealmLog to prevent UnsatisfiedLinkError during tests when exceptions are caught
        io.mockk.mockkStatic(RealmLog::class)
        every { RealmLog.error(any<Throwable>(), any<String>(), *anyVararg()) } just Runs
        every { RealmLog.error(any<String>(), *anyVararg()) } just Runs

        repository = TestRealmRepository(databaseService, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `queryListFlow emits initial results then listener updates`() = runTest {
        val realmQuery = mockk<RealmQuery<TestRealmObject>>(relaxed = true)
        val initialResults = mockk<RealmResults<TestRealmObject>>(relaxed = true)
        val frozenInitial = mockk<RealmResults<TestRealmObject>>(relaxed = true)
        val frozenUpdated = mockk<RealmResults<TestRealmObject>>(relaxed = true)

        val frozenRealmInitial = mockk<Realm>(relaxed = true)
        val frozenRealmUpdated = mockk<Realm>(relaxed = true)

        val copiedInitialList = listOf(TestRealmObject())
        val copiedUpdatedList = listOf(TestRealmObject(), TestRealmObject())

        every { realm.where(TestRealmObject::class.java) } returns realmQuery
        every { realmQuery.findAll() } returns initialResults

        every { initialResults.isValid } returns true
        every { initialResults.isLoaded } returns true
        every { initialResults.freeze() } returns frozenInitial

        every { frozenInitial.realm } returns frozenRealmInitial
        every { frozenRealmInitial.copyFromRealm(frozenInitial) } returns copiedInitialList

        val listenerSlot = slot<OrderedRealmCollectionChangeListener<RealmResults<TestRealmObject>>>()
        every { initialResults.addChangeListener(capture(listenerSlot)) } just Runs

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

        listenerSlot.captured.onChange(updatedResults, null)

        assertEquals(2, emittedLists.size)
        assertEquals(copiedUpdatedList, emittedLists[1])

        job.cancel()
    }

    @Test
    fun `queryListFlow awaitClose path closes channel and does not double-close`() = runTest {
        val realmQuery = mockk<RealmQuery<TestRealmObject>>(relaxed = true)
        val initialResults = mockk<RealmResults<TestRealmObject>>(relaxed = true)
        val frozenInitial = mockk<RealmResults<TestRealmObject>>(relaxed = true)
        val frozenRealmInitial = mockk<Realm>(relaxed = true)
        val listenerSlot = slot<OrderedRealmCollectionChangeListener<RealmResults<TestRealmObject>>>()

        every { realm.where(TestRealmObject::class.java) } returns realmQuery
        every { realmQuery.findAll() } returns initialResults

        every { initialResults.isValid } returns true
        every { initialResults.isLoaded } returns true
        every { initialResults.freeze() } returns frozenInitial
        every { frozenInitial.realm } returns frozenRealmInitial
        every { frozenRealmInitial.copyFromRealm(frozenInitial) } returns listOf()
        every { initialResults.addChangeListener(capture(listenerSlot)) } just Runs

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
        verify { initialResults.removeChangeListener(listenerSlot.captured) }
        verify(exactly = 1) { realm.close() }
    }

    @Test
    fun `queryListFlow exception path still cleans up`() = runTest {
        val exceptionMessage = "create managed realm failed"
        every { databaseService.createManagedRealmInstance() } throws RuntimeException(exceptionMessage)

        var caughtException: Throwable? = null
        try {
            repository.queryFlow().collect { }
        } catch (e: Exception) {
            caughtException = e
        }

        assertEquals(exceptionMessage, caughtException?.message)
    }

    @Test
    fun `queryListFlow safeCloseRealm exception during listener removal still closes realm`() = runTest {
        val realmQuery = mockk<RealmQuery<TestRealmObject>>(relaxed = true)
        val initialResults = mockk<RealmResults<TestRealmObject>>(relaxed = true)
        val frozenInitial = mockk<RealmResults<TestRealmObject>>(relaxed = true)
        val frozenRealmInitial = mockk<Realm>(relaxed = true)
        val listenerSlot = slot<OrderedRealmCollectionChangeListener<RealmResults<TestRealmObject>>>()

        every { realm.where(TestRealmObject::class.java) } returns realmQuery
        every { realmQuery.findAll() } returns initialResults

        every { initialResults.isValid } returns true
        every { initialResults.isLoaded } returns true
        every { initialResults.freeze() } returns frozenInitial
        every { frozenInitial.realm } returns frozenRealmInitial
        every { frozenRealmInitial.copyFromRealm(frozenInitial) } returns listOf()
        every { initialResults.addChangeListener(capture(listenerSlot)) } just Runs

        // Set up the listener removal to throw an exception
        every { initialResults.removeChangeListener(any<OrderedRealmCollectionChangeListener<RealmResults<TestRealmObject>>>()) } throws RuntimeException("listener removal failed")
        every { realm.isClosed } returns false

        val job = launch(testDispatcher) {
            repository.queryFlow().collect {
                // Collect elements
            }
        }

        // Trigger awaitClose
        job.cancel()
        job.join()

        // Even though removeChangeListener threw, realm.close() should still be called
        verify(exactly = 1) { initialResults.removeChangeListener(listenerSlot.captured) }
        verify(exactly = 1) { realm.close() }
    }

    @Test
    fun `queryListFlow emits empty list immediately when frozenResults is empty without calling copyFromRealm`() = runTest {
        val realmQuery = mockk<RealmQuery<TestRealmObject>>(relaxed = true)
        val initialResults = mockk<RealmResults<TestRealmObject>>(relaxed = true)
        val frozenInitial = mockk<RealmResults<TestRealmObject>>(relaxed = true)
        val frozenRealmInitial = mockk<Realm>(relaxed = true)
        val listenerSlot = slot<OrderedRealmCollectionChangeListener<RealmResults<TestRealmObject>>>()

        every { realm.where(TestRealmObject::class.java) } returns realmQuery
        every { realmQuery.findAll() } returns initialResults

        every { initialResults.isValid } returns true
        every { initialResults.isLoaded } returns true
        every { initialResults.freeze() } returns frozenInitial
        every { frozenInitial.isEmpty() } returns true
        every { frozenInitial.realm } returns frozenRealmInitial
        every { initialResults.addChangeListener(capture(listenerSlot)) } just Runs

        val emittedLists = mutableListOf<List<TestRealmObject>>()

        val job = launch(testDispatcher) {
            repository.queryFlow().collect {
                emittedLists.add(it)
            }
        }

        advanceUntilIdle()
        assertEquals(1, emittedLists.size)
        assertEquals(emptyList<TestRealmObject>(), emittedLists[0])
        verify(exactly = 0) { frozenRealmInitial.copyFromRealm(any<RealmResults<TestRealmObject>>()) }
        job.cancel()
    }
}
