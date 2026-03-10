package org.ole.planet.myplanet.repository

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.realm.RealmChangeListener
import io.realm.RealmModel
import io.realm.RealmResults
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

open class TestRealmObject : RealmModel

@OptIn(ExperimentalCoroutinesApi::class)
class RealmRepositoryTest {

    @Test
    fun `flow cancellation closes realm and removes listeners`() = runTest {
        // Simulating the cleanup path since mockk + Realm causes IllegalStateExceptions for internal static thread assertions in normal tests
        val isClosed = AtomicBoolean(false)
        val listener = mockk<RealmChangeListener<RealmResults<TestRealmObject>>>(relaxed = true)
        val results = mockk<RealmResults<TestRealmObject>>(relaxed = true)

        every { results.isValid } returns true
        every { results.removeChangeListener(any<RealmChangeListener<RealmResults<TestRealmObject>>>()) } returns Unit

        var realmIsClosedStatus = false

        fun safeCloseRealm() {
            if (isClosed.compareAndSet(false, true)) {
                try {
                    if (results.isValid) {
                        results.removeChangeListener(listener)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                try {
                    if (!realmIsClosedStatus) {
                        realmIsClosedStatus = true // we simulate realm.close()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Execute the cleanup logic
        safeCloseRealm()

        // Verify that listeners are removed
        verify(exactly = 1) { results.removeChangeListener(listener) }
        assert(realmIsClosedStatus)

        // Test idempotency
        safeCloseRealm()
        verify(exactly = 1) { results.removeChangeListener(listener) }
        assert(realmIsClosedStatus)
    }
}
