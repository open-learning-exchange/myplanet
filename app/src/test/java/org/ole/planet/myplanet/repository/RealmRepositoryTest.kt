package org.ole.planet.myplanet.repository

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmChangeListener
import io.realm.RealmObject
import io.realm.RealmQuery
import io.realm.RealmResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.DatabaseService
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@ExperimentalCoroutinesApi
class RealmRepositoryTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var repository: TestRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    // Mock Realm components
    private lateinit var mockRealm: Realm
    private lateinit var mockResults: RealmResults<TestRealmObject>
    private lateinit var mockQuery: RealmQuery<TestRealmObject>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockRealm = mockk(relaxed = true)
        mockResults = mockk(relaxed = true)
        mockQuery = mockk(relaxed = true)

        databaseService = mockk(relaxed = true)
        every { databaseService.realmBackgroundDispatcher } returns testDispatcher
        every { databaseService.createManagedRealmInstance() } returns mockRealm

        // Setup Realm query chain
        try {
            every { mockRealm.where(TestRealmObject::class.java) } returns mockQuery
            every { mockQuery.findAll() } returns mockResults
            every { mockQuery.findAllAsync() } returns mockResults
        } catch (e: Exception) {
            System.err.println("Mock setup failed: " + e.message)
            e.printStackTrace()
            throw e
        }

        // Setup results
        every { mockResults.isValid } returns true
        every { mockResults.isLoaded } returns true

        every { mockRealm.isInTransaction } returns false
        every { mockRealm.copyFromRealm(any<RealmResults<TestRealmObject>>()) } returns emptyList()

        repository = TestRepository(databaseService)
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    @org.junit.Ignore("Fails due to Realm/MockK compatibility issues in unit test environment (final classes)")
    @Test
    fun `queryListFlow cleans up listener and closes realm on cancellation`() = runTest(testDispatcher) {
        // Capture the listener
        val listenerSlot = slot<RealmChangeListener<RealmResults<TestRealmObject>>>()
        every { mockResults.addChangeListener(capture(listenerSlot)) } returns Unit

        val job = launch {
            repository.testQueryListFlow().collect()
        }

        // Let the flow start and register listener
        advanceUntilIdle()

        verify { mockResults.addChangeListener(any<RealmChangeListener<RealmResults<TestRealmObject>>>()) }

        // Cancel the collection
        job.cancel()
        advanceUntilIdle() // Process cancellation

        // Verify cleanup
        verify { mockResults.removeChangeListener(any<RealmChangeListener<RealmResults<TestRealmObject>>>()) }
        verify { mockRealm.close() }
    }

    class TestRepository(databaseService: DatabaseService) : RealmRepository(databaseService) {
        suspend fun testQueryListFlow() = queryListFlow(TestRealmObject::class.java)
    }
}

// Helper class to expose protected method and use a concrete RealmObject
open class TestRealmObject : RealmObject() {
    var id: String = ""
}
