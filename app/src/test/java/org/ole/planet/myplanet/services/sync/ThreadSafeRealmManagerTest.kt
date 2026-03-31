package org.ole.planet.myplanet.services.sync

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.realm.Realm
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService

class ThreadSafeRealmManagerTest {

    private lateinit var databaseService: DatabaseService

    @Before
    fun setup() {
        databaseService = mockk()
        // Ensure starting clean for each test
        ThreadSafeRealmManager.closeThreadRealm()
    }

    @Test
    fun `withRealm creates new instance when empty`() {
        val realm: Realm = mockk(relaxed = true)
        every { realm.isClosed } returns false
        every { databaseService.createManagedRealmInstance() } returns realm

        val result = ThreadSafeRealmManager.withRealm(databaseService) { r ->
            assertEquals(realm, r)
            "success"
        }

        assertEquals("success", result)
        verify(exactly = 1) { databaseService.createManagedRealmInstance() }
    }

    @Test
    fun `withRealm reuses open instance on same thread`() {
        val realm: Realm = mockk(relaxed = true)
        every { realm.isClosed } returns false
        every { databaseService.createManagedRealmInstance() } returns realm

        // First call creates it
        ThreadSafeRealmManager.withRealm(databaseService) { }

        // Second call should reuse it
        val result = ThreadSafeRealmManager.withRealm(databaseService) { r ->
            assertEquals(realm, r)
            "reused"
        }

        assertEquals("reused", result)
        // Should only be called once across both withRealm invocations
        verify(exactly = 1) { databaseService.createManagedRealmInstance() }
    }

    @Test
    fun `withRealm creates new instance when existing is closed`() {
        val realm1: Realm = mockk(relaxed = true)
        val realm2: Realm = mockk(relaxed = true)

        // First instance is closed
        every { realm1.isClosed } returns true
        every { realm2.isClosed } returns false

        // Return realm1 first, then realm2
        every { databaseService.createManagedRealmInstance() } returnsMany listOf(realm1, realm2)

        // Seed the thread local with the closed realm
        ThreadSafeRealmManager.withRealm(databaseService) { }

        // Second call should see it's closed and request a new one
        val result = ThreadSafeRealmManager.withRealm(databaseService) { r ->
            assertEquals(realm2, r)
            "new_instance"
        }

        assertEquals("new_instance", result)
        verify(exactly = 2) { databaseService.createManagedRealmInstance() }
    }

    @Test
    fun `withRealm provides thread isolation`() {
        val realm1: Realm = mockk(relaxed = true)
        val realm2: Realm = mockk(relaxed = true)

        every { realm1.isClosed } returns false
        every { realm2.isClosed } returns false

        every { databaseService.createManagedRealmInstance() } returnsMany listOf(realm1, realm2)

        val thread1Result = AtomicReference<Realm>()
        val thread2Result = AtomicReference<Realm>()
        val latch = CountDownLatch(2)

        val thread1 = Thread {
            ThreadSafeRealmManager.withRealm(databaseService) { r ->
                thread1Result.set(r)
            }
            latch.countDown()
        }

        val thread2 = Thread {
            ThreadSafeRealmManager.withRealm(databaseService) { r ->
                thread2Result.set(r)
            }
            latch.countDown()
        }

        thread1.start()
        thread2.start()
        latch.await()

        // Verify each thread got a distinct instance
        assertEquals(realm1, thread1Result.get())
        assertEquals(realm2, thread2Result.get())
        assertNotEquals(thread1Result.get(), thread2Result.get())

        verify(exactly = 2) { databaseService.createManagedRealmInstance() }
    }

    @Test
    fun `withRealm handles exceptions safely`() {
        val realm: Realm = mockk(relaxed = true)
        every { realm.isClosed } returns false
        every { databaseService.createManagedRealmInstance() } returns realm

        val result = ThreadSafeRealmManager.withRealm(databaseService) { r ->
            throw RuntimeException("Test exception")
        }

        assertNull(result)
    }

    @Test
    fun `closeThreadRealm closes open instance`() {
        val realm: Realm = mockk(relaxed = true)
        every { realm.isClosed } returns false
        every { databaseService.createManagedRealmInstance() } returns realm

        // Populate the thread local
        ThreadSafeRealmManager.withRealm(databaseService) { r -> }

        // Close it
        ThreadSafeRealmManager.closeThreadRealm()

        verify { realm.close() }

        // Verify it was removed by checking if a new one is created next time
        val newRealm: Realm = mockk(relaxed = true)
        every { newRealm.isClosed } returns false
        every { databaseService.createManagedRealmInstance() } returns newRealm

        ThreadSafeRealmManager.withRealm(databaseService) { r ->
            assertEquals(newRealm, r)
        }

        verify(exactly = 2) { databaseService.createManagedRealmInstance() }
    }

    @Test
    fun `closeThreadRealm ignores already closed instance`() {
        val realm: Realm = mockk(relaxed = true)
        every { realm.isClosed } returns true
        every { databaseService.createManagedRealmInstance() } returns realm

        // Populate the thread local
        ThreadSafeRealmManager.withRealm(databaseService) { r -> }

        // Close it
        ThreadSafeRealmManager.closeThreadRealm()

        // Should not call close() again since it's already closed
        verify(exactly = 0) { realm.close() }
    }
}
