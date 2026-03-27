package org.ole.planet.myplanet.model

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.lang.RuntimeException

class RealmRemovedLogTest {

    @Test
    fun testOnAddThrowsExceptionAndCancelsTransaction() {
        val mockRealm = mockk<Realm>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmRemovedLog>>()
        val mockResults = mockk<RealmResults<RealmRemovedLog>>()

        every { mockRealm.isInTransaction } returns false // To trigger startedTransaction = true
        every { mockRealm.where(RealmRemovedLog::class.java) } returns mockQuery
        every { mockQuery.equalTo("type", any<String>()) } returns mockQuery
        every { mockQuery.equalTo("userId", any<String>()) } returns mockQuery
        every { mockQuery.equalTo("docId", any<String>()) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults

        val expectedException = RuntimeException("Test Exception")
        every { mockResults.deleteAllFromRealm() } throws expectedException

        // mockRealm.isInTransaction is checked again in the catch block
        every { mockRealm.isInTransaction } returnsMany listOf(false, true)

        val exception = assertThrows(RuntimeException::class.java) {
            RealmRemovedLog.onAdd(mockRealm, "testType", "testUserId", "testDocId")
        }

        assertEquals(expectedException, exception)

        verify(exactly = 1) { mockRealm.beginTransaction() }
        verify(exactly = 1) { mockRealm.cancelTransaction() }
        verify(exactly = 0) { mockRealm.commitTransaction() }
    }
}
