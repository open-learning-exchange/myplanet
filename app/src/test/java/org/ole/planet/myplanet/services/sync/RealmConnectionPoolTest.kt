package org.ole.planet.myplanet.services.sync

import android.content.Context
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.realm.Realm
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService

@OptIn(ExperimentalCoroutinesApi::class)
class RealmConnectionPoolTest {

    private lateinit var context: Context
    private lateinit var databaseService: DatabaseService
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = mockk()
        databaseService = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `useRealm acquires and releases connection correctly`() = runTest {
        val mockRealm = mockk<Realm>()
        every { mockRealm.isClosed } returns false
        every { mockRealm.isInTransaction } returns false
        every { mockRealm.close() } just Runs
        every { databaseService.createManagedRealmInstance() } returns mockRealm

        val config = RealmPoolConfig(maxConnections = 2)
        val pool = RealmConnectionPool(context, databaseService, config)

        var usedRealm: Realm? = null
        pool.useRealm { realm ->
            usedRealm = realm
        }

        assertEquals(mockRealm, usedRealm)
        verify(exactly = 1) { databaseService.createManagedRealmInstance() }
    }

    @Test
    fun `useRealm reuses existing connection on same thread`() = runTest {
        val mockRealm = mockk<Realm>()
        every { mockRealm.isClosed } returns false
        every { mockRealm.isInTransaction } returns false
        every { mockRealm.close() } just Runs
        every { databaseService.createManagedRealmInstance() } returns mockRealm

        val config = RealmPoolConfig(maxConnections = 2)
        val pool = RealmConnectionPool(context, databaseService, config)

        pool.useRealm { realm1 ->
            pool.useRealm { realm2 ->
                assertEquals(realm1, realm2)
            }
        }

        // Ensure it's only created once
        verify(exactly = 1) { databaseService.createManagedRealmInstance() }
    }

    @Test
    fun `pool creates multiple connections up to maxConnections`() = runBlocking {
        val mockRealm1 = mockk<Realm>()
        every { mockRealm1.isClosed } returns false
        every { mockRealm1.isInTransaction } returns false
        every { mockRealm1.close() } just Runs

        val mockRealm2 = mockk<Realm>()
        every { mockRealm2.isClosed } returns false
        every { mockRealm2.isInTransaction } returns false
        every { mockRealm2.close() } just Runs

        val callCounter = AtomicInteger(0)
        every { databaseService.createManagedRealmInstance() } answers {
            if (callCounter.getAndIncrement() == 0) mockRealm1 else mockRealm2
        }

        val config = RealmPoolConfig(maxConnections = 2)
        val pool = RealmConnectionPool(context, databaseService, config)

        val releaseJobs = CompletableDeferred<Unit>()
        val job1Acquired = CompletableDeferred<Unit>()
        val job2Acquired = CompletableDeferred<Unit>()

        // Explicit threads are used here to bypass `ThreadLocal` connection caching, forcing the
        // connection pool to generate distinct connections up to its maximum concurrent limit.
        val dispatcher1 = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val dispatcher2 = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

        try {
            val job1 = async(dispatcher1) {
                pool.useRealm {
                    job1Acquired.complete(Unit)
                    releaseJobs.await()
                }
            }
            val job2 = async(dispatcher2) {
                pool.useRealm {
                    job2Acquired.complete(Unit)
                    releaseJobs.await()
                }
            }

            // Wait for both connections to be checked out simultaneously
            awaitAll(job1Acquired, job2Acquired)

            // Allow both coroutines to complete
            releaseJobs.complete(Unit)
            awaitAll(job1, job2)

            verify(exactly = 2) { databaseService.createManagedRealmInstance() }
        } finally {
            dispatcher1.close()
            dispatcher2.close()
        }
    }

    @Test
    fun `pool validates connections and removes invalid ones`() = runTest {
        val mockRealmInvalid = mockk<Realm>()
        // Return false for isClosed so the pool attempts to close it,
        // but true for isInTransaction so isConnectionValid() returns false
        every { mockRealmInvalid.isClosed } returns false
        every { mockRealmInvalid.isInTransaction } returns true
        every { mockRealmInvalid.close() } just Runs

        val mockRealmOpen = mockk<Realm>()
        every { mockRealmOpen.isClosed } returns false
        every { mockRealmOpen.isInTransaction } returns false
        every { mockRealmOpen.close() } just Runs

        var callCount = 0
        every { databaseService.createManagedRealmInstance() } answers {
            if (callCount++ == 0) mockRealmInvalid else mockRealmOpen
        }

        val config = RealmPoolConfig(maxConnections = 2, enableConnectionValidation = true)
        val pool = RealmConnectionPool(context, databaseService, config)

        pool.useRealm { }

        var usedRealm: Realm? = null
        pool.useRealm { realm ->
            usedRealm = realm
        }

        assertEquals(mockRealmOpen, usedRealm)
        verify(exactly = 2) { databaseService.createManagedRealmInstance() }
        verify(exactly = 1) { mockRealmInvalid.close() }
    }

    @Test
    fun `pool exhausts connections and suspends`() = runBlocking {
        val mockRealm = mockk<Realm>()
        every { mockRealm.isClosed } returns false
        every { mockRealm.isInTransaction } returns false
        every { mockRealm.close() } just Runs
        every { databaseService.createManagedRealmInstance() } returns mockRealm

        val config = RealmPoolConfig(maxConnections = 1)
        val pool = RealmConnectionPool(context, databaseService, config)

        val secondTaskCompleted = AtomicBoolean(false)
        val job1Acquired = CompletableDeferred<Unit>()
        val releaseJob1 = CompletableDeferred<Unit>()
        val job2AttemptingToAcquire = CompletableDeferred<Unit>()

        // We use explicit SingleThreadExecutors to ensure job1 and job2 run on distinct physical threads.
        // This purposefully bypasses the `ThreadLocal` fast-path in `RealmConnectionPool.useRealm`
        // so we can test the actual semaphore locking mechanism when maxConnections is reached.
        val dispatcher1 = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val dispatcher2 = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

        try {
            val job1 = async(dispatcher1) {
                pool.useRealm {
                    job1Acquired.complete(Unit)
                    releaseJob1.await()
                }
            }

            // Wait until job1 has definitively acquired the only connection
            job1Acquired.await()

            // This should suspend inside pool.useRealm since the single connection is checked out
            val job2 = async(dispatcher2) {
                job2AttemptingToAcquire.complete(Unit) // Handshake: job2 is running, about to ask for a connection
                pool.useRealm {
                    secondTaskCompleted.set(true)
                }
            }

            // Wait structurally until job2 has started and is about to hit the suspension point
            job2AttemptingToAcquire.await()

            // Yield briefly to let the underlying semaphore coroutine machinery execute the suspension.
            // (While technically a wall-clock delay, it's after the job has proven it started, eliminating the main race condition).
            // A better structural proof is to ensure secondTaskCompleted remains false even when we actively wait.
            // If the lock fails, it would race to true immediately.
            delay(10)

            // Structurally, job2 cannot proceed until job1 releases the permit.
            assertTrue(!secondTaskCompleted.get())

            // Release job1 so job2 can acquire the permit and finish
            releaseJob1.complete(Unit)

            awaitAll(job1, job2)

            // job2 should have gotten the permit and completed
            assertTrue(secondTaskCompleted.get())
            verify(exactly = 1) { databaseService.createManagedRealmInstance() }
        } finally {
            dispatcher1.close()
            dispatcher2.close()
        }
    }

    @Test
    fun `pool cleans up expired connections during validation`() = runTest {
        var calls = 0
        every { databaseService.createManagedRealmInstance() } answers {
            calls++
            mockk<Realm> {
                every { isClosed } returns false
                every { isInTransaction } returns false
                every { close() } just Runs
            }
        }

        // Set idle timeout very low to expire connections immediately
        val config = RealmPoolConfig(
            maxConnections = 2,
            idleTimeoutMs = 1,
            validationIntervalMs = 10,
            enableConnectionValidation = true
        )
        val pool = RealmConnectionPool(context, databaseService, config)

        // Create connection, put it in pool
        pool.useRealm { }

        assertEquals(1, calls)

        // Wait longer than idle timeout + validation interval
        // Use Thread.sleep because RealmConnectionPool uses System.currentTimeMillis() internally
        Thread.sleep(50)

        // Next use should trigger validation, see connection is expired, close it, and create new one
        pool.useRealm { }

        // Should have created 2 connections total (1st expired, 2nd created)
        assertEquals(2, calls)
    }

    @Test
    fun `isConnectionValid returns false if realm is in transaction`() = runTest {
        val mockRealm = mockk<Realm>()
        // Valid for first request
        every { mockRealm.isClosed } returns false
        // In transaction for second request
        every { mockRealm.isInTransaction } returnsMany listOf(false, true)
        every { mockRealm.close() } just Runs

        var calls = 0
        every { databaseService.createManagedRealmInstance() } answers {
            calls++
            if (calls == 1) mockRealm else {
                mockk {
                    every { isClosed } returns false
                    every { isInTransaction } returns false
                    every { close() } just Runs
                }
            }
        }

        val config = RealmPoolConfig(maxConnections = 2, enableConnectionValidation = true)
        val pool = RealmConnectionPool(context, databaseService, config)

        pool.useRealm { }

        // Should create a new one because the first one is "in transaction" and thus invalid
        pool.useRealm { }

        assertEquals(2, calls)
    }
}
