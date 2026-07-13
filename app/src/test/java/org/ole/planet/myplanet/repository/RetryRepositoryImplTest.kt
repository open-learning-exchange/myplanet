package org.ole.planet.myplanet.repository

import io.mockk.coEvery
import io.mockk.every
import io.mockk.invoke
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.After
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmRetryOperation
import org.ole.planet.myplanet.model.RetryFailure
import org.ole.planet.myplanet.utils.TestTimeProvider
import org.ole.planet.myplanet.utils.MainDispatcherRule

@Suppress("UNCHECKED_CAST")
class RetryRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    private lateinit var databaseService: DatabaseService
    private lateinit var repository: RetryRepositoryImpl
    private val testDispatcher = mainDispatcherRule.testDispatcher
    private val timeProvider = TestTimeProvider(currentTime = 1_700_000_000_000L)

    @org.junit.After
    fun tearDown() {
        io.mockk.unmockkAll()
    }

    @Before
    fun setUp() {
        databaseService = mockk(relaxed = true)
        repository = RetryRepositoryImpl(databaseService, testDispatcher, timeProvider)
    }

    @Test
    fun `enqueue invokes transaction and creates operation`() = runTest {
        val realm = mockk<Realm>(relaxed = true)
        val transactionSlot = slot<(Realm) -> Unit>()
        coEvery { databaseService.executeTransactionAsync(capture(transactionSlot)) } answers {
            transactionSlot.captured.invoke(realm)
        }

        val retryFailure = RetryFailure("itemId", "test error", 500)

        val op = mockk<RealmRetryOperation>(relaxed = true)
        every { realm.createObject(RealmRetryOperation::class.java, any()) } returns op



        repository.enqueue(
            "testUploadType", retryFailure, "testPayload", "testEndpoint",
            "POST", "testDbId", "TestClass", "testUserId"
        )

        verify { realm.createObject(RealmRetryOperation::class.java, any()) }
        verify {
            op.uploadType = "testUploadType"
            op.serializedPayload = "testPayload"
            op.endpoint = "testEndpoint"
            op.httpMethod = "POST"
            op.dbId = "testDbId"
            op.modelClassName = "TestClass"
            op.userId = "testUserId"
            op.status = RealmRetryOperation.STATUS_PENDING
            op.attemptCount = 1
            op.httpCode = 500
        }
    }

    @Test
    fun `updateAttempt updates operation fields`() = runTest {
        val realm = mockk<Realm>(relaxed = true)
        val query = mockk<RealmQuery<RealmRetryOperation>>()
        val operation = RealmRetryOperation().apply {
            attemptCount = 1
            maxAttempts = 5
        }

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val block = invocation.args[0] as ((Realm) -> Unit)
            block.invoke(realm)
        }

        every { realm.where(RealmRetryOperation::class.java) } returns query
        every { query.equalTo("id", "opId") } returns query
        every { query.findFirst() } returns operation

        val retryFailure = RetryFailure("itemId", "Test Error", 503)

        repository.updateAttempt("opId", retryFailure)

        assertEquals(2, operation.attemptCount)
        assertEquals("Test Error", operation.errorMessage)
        assertEquals(503, operation.httpCode)
        // Check nextRetryTime was calculated properly (we assume calculateNextRetryTime sets a future time)
        assert(operation.nextRetryTime > 0)
    }

    @Test
    fun `updateAttempt changes status to abandoned when max attempts reached`() = runTest {
        val realm = mockk<Realm>(relaxed = true)
        val query = mockk<RealmQuery<RealmRetryOperation>>()
        val operation = RealmRetryOperation().apply {
            attemptCount = 4
            maxAttempts = 5
            status = RealmRetryOperation.STATUS_PENDING
        }

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val block = invocation.args[0] as ((Realm) -> Unit)
            block.invoke(realm)
        }

        every { realm.where(RealmRetryOperation::class.java) } returns query
        every { query.equalTo("id", "opId") } returns query
        every { query.findFirst() } returns operation

        val retryFailure = RetryFailure("itemId", "Unknown error", null)

        repository.updateAttempt("opId", retryFailure)

        assertEquals(5, operation.attemptCount)
        assertEquals(RealmRetryOperation.STATUS_ABANDONED, operation.status)
    }

    @Test
    fun `markInProgress updates status`() = runTest {
        val realm = mockk<Realm>(relaxed = true)
        val query = mockk<RealmQuery<RealmRetryOperation>>()
        val operation = RealmRetryOperation().apply {
            status = RealmRetryOperation.STATUS_PENDING
        }

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val block = invocation.args[0] as ((Realm) -> Unit)
            block.invoke(realm)
        }

        every { realm.where(RealmRetryOperation::class.java) } returns query
        every { query.equalTo("id", "opId") } returns query
        every { query.findFirst() } returns operation

        repository.markInProgress("opId")

        assertEquals(RealmRetryOperation.STATUS_IN_PROGRESS, operation.status)
    }

    @Test
    fun `markCompleted updates status and timestamp`() = runTest {
        val realm = mockk<Realm>(relaxed = true)
        val query = mockk<RealmQuery<RealmRetryOperation>>()
        val operation = RealmRetryOperation().apply {
            status = RealmRetryOperation.STATUS_PENDING
            lastAttemptTime = 0
        }

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val block = invocation.args[0] as ((Realm) -> Unit)
            block.invoke(realm)
        }

        every { realm.where(RealmRetryOperation::class.java) } returns query
        every { query.equalTo("id", "opId") } returns query
        every { query.findFirst() } returns operation

        repository.markCompleted("opId")

        assertEquals(RealmRetryOperation.STATUS_COMPLETED, operation.status)
        assert(operation.lastAttemptTime > 0)
    }

    @Test
    fun `markFailed updates status to pending when attempts remain`() = runTest {
        val realm = mockk<Realm>(relaxed = true)
        val query = mockk<RealmQuery<RealmRetryOperation>>()
        val operation = RealmRetryOperation().apply {
            attemptCount = 1
            maxAttempts = 5
            status = RealmRetryOperation.STATUS_IN_PROGRESS
        }

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val block = invocation.args[0] as ((Realm) -> Unit)
            block.invoke(realm)
        }

        every { realm.where(RealmRetryOperation::class.java) } returns query
        every { query.equalTo("id", "opId") } returns query
        every { query.findFirst() } returns operation

        repository.markFailed("opId", "Fail reason", 404)

        assertEquals(2, operation.attemptCount)
        assertEquals("Fail reason", operation.errorMessage)
        assertEquals(404, operation.httpCode)
        assertEquals(RealmRetryOperation.STATUS_PENDING, operation.status)
        assert(operation.nextRetryTime > 0)
    }

    @Test
    fun `markFailed updates status to abandoned when max attempts reached`() = runTest {
        val realm = mockk<Realm>(relaxed = true)
        val query = mockk<RealmQuery<RealmRetryOperation>>()
        val operation = RealmRetryOperation().apply {
            attemptCount = 4
            maxAttempts = 5
            status = RealmRetryOperation.STATUS_IN_PROGRESS
        }

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val block = invocation.args[0] as ((Realm) -> Unit)
            block.invoke(realm)
        }

        every { realm.where(RealmRetryOperation::class.java) } returns query
        every { query.equalTo("id", "opId") } returns query
        every { query.findFirst() } returns operation

        repository.markFailed("opId", "Fail reason", 500)

        assertEquals(5, operation.attemptCount)
        assertEquals(RealmRetryOperation.STATUS_ABANDONED, operation.status)
    }

    @Test
    fun `getPending returns filtered operations`() = runTest {
        val realm = mockk<Realm>(relaxed = true)
        val query = mockk<RealmQuery<RealmRetryOperation>>()
        val results = mockk<RealmResults<RealmRetryOperation>>()

        val operation1 = RealmRetryOperation().apply { attemptCount = 1; maxAttempts = 5 }
        // The filtering is now handled by Realm's rawPredicate, so mock the filtered result
        val list = listOf(operation1)

        val transactionSlot = slot<(Realm) -> Any>()
        coEvery { databaseService.withRealmAsync<Any>(capture(transactionSlot)) } answers {
            transactionSlot.captured.invoke(realm)
        }

        every { realm.where(RealmRetryOperation::class.java) } returns query
        every { query.equalTo("status", RealmRetryOperation.STATUS_PENDING) } returns query
        every { query.lessThanOrEqualTo("nextRetryTime", any<Long>()) } returns query
        every { query.rawPredicate("attemptCount < maxAttempts") } returns query
        every { query.findAll() } returns results
        every { results.iterator() } returns list.toMutableList().iterator()
        every { results.size } returns list.size
        every { realm.copyFromRealm(any<Iterable<RealmRetryOperation>>()) } answers {
            list
        }

        val pending = repository.getPending()

        assertEquals(1, pending.size)
        assertEquals(operation1, pending[0])
    }

    @Test
    fun `getPendingCount returns correct total count`() = runTest {
        val realm = mockk<Realm>(relaxed = true)
        val query = mockk<RealmQuery<RealmRetryOperation>>()

        val transactionSlot = slot<(Realm) -> Any>()
        coEvery { databaseService.withRealmAsync<Any>(capture(transactionSlot)) } answers {
            transactionSlot.captured.invoke(realm)
        }

        every { realm.where(RealmRetryOperation::class.java) } returns query
        every { query.equalTo("status", RealmRetryOperation.STATUS_PENDING) } returns query
        every { query.or() } returns query
        every { query.equalTo("status", RealmRetryOperation.STATUS_IN_PROGRESS) } returns query
        every { query.count() } returns 10L

        val count = repository.getPendingCount()

        assertEquals(10L, count)
    }

    @Test
    fun `getExistingOperation finds operation not completed or abandoned`() = runTest {
        val realm = mockk<Realm>(relaxed = true)
        val query = mockk<RealmQuery<RealmRetryOperation>>()
        val operation = RealmRetryOperation()

        val transactionSlot = slot<(Realm) -> Any>()
        coEvery { databaseService.withRealmAsync<Any>(capture(transactionSlot)) } answers {
            transactionSlot.captured.invoke(realm)
        }

        every { realm.where(RealmRetryOperation::class.java) } returns query
        every { query.equalTo("itemId", "item123") } returns query
        every { query.equalTo("uploadType", "typeA") } returns query
        every { query.notEqualTo("status", RealmRetryOperation.STATUS_COMPLETED) } returns query
        every { query.notEqualTo("status", RealmRetryOperation.STATUS_ABANDONED) } returns query
        every { query.findFirst() } returns operation
        every { realm.copyFromRealm(operation) } returns operation

        val result = repository.getExistingOperation("item123", "typeA")

        assertEquals(operation, result)
    }

    @Test
    fun `cleanup deletes old completed operations`() = runTest {
        val realm = mockk<Realm>(relaxed = true)
        val query = mockk<RealmQuery<RealmRetryOperation>>()
        val results = mockk<RealmResults<RealmRetryOperation>>()

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val block = invocation.args[0] as ((Realm) -> Unit)
            block.invoke(realm)
        }

        every { realm.where(RealmRetryOperation::class.java) } returns query
        every { query.equalTo("status", RealmRetryOperation.STATUS_COMPLETED) } returns query
        every { query.lessThan("lastAttemptTime", any<Long>()) } returns query
        every { query.findAll() } returns results
        every { results.deleteAllFromRealm() } returns true
        repository.cleanup()

        verify { results.deleteAllFromRealm() }
    }

    @Test
    fun `resetAllPending updates nextRetryTime`() = runTest {
        val realm = mockk<Realm>(relaxed = true)
        val query = mockk<RealmQuery<RealmRetryOperation>>()
        val results = mockk<RealmResults<RealmRetryOperation>>()

        val operation1 = RealmRetryOperation().apply { nextRetryTime = 0 }
        val operation2 = RealmRetryOperation().apply { nextRetryTime = 0 }

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val block = invocation.args[0] as ((Realm) -> Unit)
            block.invoke(realm)
        }

        every { realm.where(RealmRetryOperation::class.java) } returns query
        every { query.equalTo("status", RealmRetryOperation.STATUS_PENDING) } returns query
        every { query.findAll() } returns results
        every { results.iterator() } answers { mutableListOf(operation1, operation2).iterator() }

        repository.resetAllPending()

        assert(operation1.nextRetryTime > 0)
        assert(operation2.nextRetryTime > 0)
    }

    @Test
    fun `deletePendingAndAbandonedOperations deletes matching records`() = runTest {
        val realm = mockk<Realm>(relaxed = true)
        val query = mockk<RealmQuery<RealmRetryOperation>>()
        val results = mockk<RealmResults<RealmRetryOperation>>()

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val block = invocation.args[0] as ((Realm) -> Unit)
            block.invoke(realm)
        }

        every { realm.where(RealmRetryOperation::class.java) } returns query
        every { query.equalTo("status", RealmRetryOperation.STATUS_PENDING) } returns query
        every { query.or() } returns query
        every { query.equalTo("status", RealmRetryOperation.STATUS_ABANDONED) } returns query
        every { query.findAll() } returns results
        every { results.deleteAllFromRealm() } returns true
        repository.deletePendingAndAbandonedOperations()

        verify { results.deleteAllFromRealm() }
    }

    @Test
    fun `recoverStuckOperations changes status to pending`() = runTest {
        val realm = mockk<Realm>(relaxed = true)
        val query = mockk<RealmQuery<RealmRetryOperation>>()
        val results = mockk<RealmResults<RealmRetryOperation>>()

        val operation1 = RealmRetryOperation().apply { status = RealmRetryOperation.STATUS_IN_PROGRESS; nextRetryTime = 0 }

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val block = invocation.args[0] as ((Realm) -> Unit)
            block.invoke(realm)
        }

        every { realm.where(RealmRetryOperation::class.java) } returns query
        every { query.equalTo("status", RealmRetryOperation.STATUS_IN_PROGRESS) } returns query
        every { query.findAll() } returns results
        every { results.iterator() } answers { mutableListOf(operation1).iterator() }

        repository.recoverStuckOperations()

        assertEquals(RealmRetryOperation.STATUS_PENDING, operation1.status)
        assert(operation1.nextRetryTime > 0)
    }

}
