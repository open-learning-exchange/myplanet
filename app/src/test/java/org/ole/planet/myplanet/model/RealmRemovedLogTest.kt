package org.ole.planet.myplanet.model

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import org.junit.Assert.assertThrows
import org.junit.Test

class RealmRemovedLogTest {

    @Test
    fun testOnAddThrowsExceptionAndCancelsTransaction() {
        val mockRealm = mockk<Realm>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmRemovedLog>>()
        val mockResults = mockk<RealmResults<RealmRemovedLog>>()

        // First call: false (not in transaction), Second call: true (in catch block, so cancelTransaction is called)
        every { mockRealm.isInTransaction } returnsMany listOf(false, true)

        every { mockRealm.where(RealmRemovedLog::class.java) } returns mockQuery
        every { mockQuery.equalTo("type", any<String>()) } returns mockQuery
        every { mockQuery.equalTo("userId", any<String>()) } returns mockQuery
        every { mockQuery.equalTo("docId", any<String>()) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults

        val expectedException = RuntimeException("Test Exception")
        every { mockResults.deleteAllFromRealm() } throws expectedException

        val exception = assertThrows(RuntimeException::class.java) {
            RealmRemovedLog.onAdd(mockRealm, "testType", "testUserId", "testDocId")
        }

        assert(exception == expectedException)

        verify(exactly = 1) { mockRealm.beginTransaction() }
        verify(exactly = 1) { mockRealm.cancelTransaction() }
        verify(exactly = 0) { mockRealm.commitTransaction() }
    }

    @Test
    fun testOnAddCommitsOnSuccess() {
        val mockRealm = mockk<Realm>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmRemovedLog>>()
        val mockResults = mockk<RealmResults<RealmRemovedLog>>()

        every { mockRealm.isInTransaction } returns false // Trigger startedTransaction = true

        every { mockRealm.where(RealmRemovedLog::class.java) } returns mockQuery
        every { mockQuery.equalTo("type", any<String>()) } returns mockQuery
        every { mockQuery.equalTo("userId", any<String>()) } returns mockQuery
        every { mockQuery.equalTo("docId", any<String>()) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults

        every { mockResults.deleteAllFromRealm() } returns true

        RealmRemovedLog.onAdd(mockRealm, "testType", "testUserId", "testDocId")

        verify(exactly = 1) { mockRealm.beginTransaction() }
        verify(exactly = 1) { mockRealm.commitTransaction() }
        verify(exactly = 0) { mockRealm.cancelTransaction() }
    }

    @Test
    fun testOnAddDoesNotManageTransactionIfAlreadyInOne() {
        val mockRealm = mockk<Realm>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmRemovedLog>>()
        val mockResults = mockk<RealmResults<RealmRemovedLog>>()

        every { mockRealm.isInTransaction } returns true // Trigger startedTransaction = false

        every { mockRealm.where(RealmRemovedLog::class.java) } returns mockQuery
        every { mockQuery.equalTo("type", any<String>()) } returns mockQuery
        every { mockQuery.equalTo("userId", any<String>()) } returns mockQuery
        every { mockQuery.equalTo("docId", any<String>()) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults

        every { mockResults.deleteAllFromRealm() } returns true

        RealmRemovedLog.onAdd(mockRealm, "testType", "testUserId", "testDocId")

        verify(exactly = 0) { mockRealm.beginTransaction() }
        verify(exactly = 0) { mockRealm.commitTransaction() }
        verify(exactly = 0) { mockRealm.cancelTransaction() }
    }

    @Test
    fun testOnRemoveThrowsExceptionAndCancelsTransaction() {
        val mockRealm = mockk<Realm>(relaxed = true)

        // First call: false (not in transaction), Second call: true (in catch block)
        every { mockRealm.isInTransaction } returnsMany listOf(false, true)

        val expectedException = RuntimeException("Test Exception")
        every { mockRealm.createObject(RealmRemovedLog::class.java, any<String>()) } throws expectedException

        val exception = assertThrows(RuntimeException::class.java) {
            RealmRemovedLog.onRemove(mockRealm, "testType", "testUserId", "testDocId")
        }

        assert(exception == expectedException)

        verify(exactly = 1) { mockRealm.beginTransaction() }
        verify(exactly = 1) { mockRealm.cancelTransaction() }
        verify(exactly = 0) { mockRealm.commitTransaction() }
    }

    @Test
    fun testOnRemoveCommitsOnSuccess() {
        val mockRealm = mockk<Realm>(relaxed = true)
        val mockLog = mockk<RealmRemovedLog>(relaxed = true)

        every { mockRealm.isInTransaction } returns false // Trigger startedTransaction = true
        every { mockRealm.createObject(RealmRemovedLog::class.java, any<String>()) } returns mockLog

        RealmRemovedLog.onRemove(mockRealm, "testType", "testUserId", "testDocId")

        verify(exactly = 1) { mockRealm.beginTransaction() }
        verify(exactly = 1) { mockRealm.commitTransaction() }
        verify(exactly = 0) { mockRealm.cancelTransaction() }
        verify(exactly = 1) { mockLog.docId = "testDocId" }
        verify(exactly = 1) { mockLog.userId = "testUserId" }
        verify(exactly = 1) { mockLog.type = "testType" }
    }

    @Test
    fun testOnRemoveDoesNotManageTransactionIfAlreadyInOne() {
        val mockRealm = mockk<Realm>(relaxed = true)
        val mockLog = mockk<RealmRemovedLog>(relaxed = true)

        every { mockRealm.isInTransaction } returns true // Trigger startedTransaction = false
        every { mockRealm.createObject(RealmRemovedLog::class.java, any<String>()) } returns mockLog

        RealmRemovedLog.onRemove(mockRealm, "testType", "testUserId", "testDocId")

        verify(exactly = 0) { mockRealm.beginTransaction() }
        verify(exactly = 0) { mockRealm.commitTransaction() }
        verify(exactly = 0) { mockRealm.cancelTransaction() }
        verify(exactly = 1) { mockLog.docId = "testDocId" }
        verify(exactly = 1) { mockLog.userId = "testUserId" }
        verify(exactly = 1) { mockLog.type = "testType" }
    }

    @Test
    fun testOnRemove_throwsException_cancelsTransaction() {
        val realm = mockk<Realm>(relaxed = true)

        // Initial state: not in a transaction
        every { realm.isInTransaction } returns false andThen true

        // Throw an exception when trying to create the object
        every { realm.createObject(RealmRemovedLog::class.java, any<String>()) } throws RuntimeException("Test Exception")

        assertThrows(RuntimeException::class.java) {
            RealmRemovedLog.onRemove(realm, "testType", "testUserId", "testDocId")
        }

        // Verify that cancelTransaction was called because we started the transaction and were in it when the exception occurred
        verify(exactly = 1) { realm.cancelTransaction() }
    }

    @Test
    fun testOnRemove_throwsException_doesNotCancelTransactionIfAlreadyInTransaction() {
        val realm = mockk<Realm>(relaxed = true)

        // Initial state: already in a transaction
        every { realm.isInTransaction } returns true

        // Throw an exception when trying to create the object
        every { realm.createObject(RealmRemovedLog::class.java, any<String>()) } throws RuntimeException("Test Exception")

        assertThrows(RuntimeException::class.java) {
            RealmRemovedLog.onRemove(realm, "testType", "testUserId", "testDocId")
        }

        // Verify that cancelTransaction was NOT called because we didn't start the transaction
        verify(exactly = 0) { realm.cancelTransaction() }
    }
}
