package org.ole.planet.myplanet.model

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.realm.Realm
import org.junit.Assert.assertThrows
import org.junit.Test

class RealmRemovedLogTest {

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
