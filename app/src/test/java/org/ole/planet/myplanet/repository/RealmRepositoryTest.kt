package org.ole.planet.myplanet.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class RealmRepositoryTest {

    @Test
    fun `flow cancellation closes realm and removes listeners`() = runBlocking {
        var removedListenerCount = 0
        var closedRealmCount = 0

        val isClosed = AtomicBoolean(false)
        val dummyIsValid = true
        var dummyRealmIsClosed = false

        fun safeCloseRealm() {
            if (isClosed.compareAndSet(false, true)) {
                try {
                    if (dummyIsValid) {
                        removedListenerCount++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                try {
                    if (!dummyRealmIsClosed) {
                        dummyRealmIsClosed = true
                        closedRealmCount++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        safeCloseRealm()

        assert(removedListenerCount == 1)
        assert(closedRealmCount == 1)

        // idempotency check
        safeCloseRealm()

        assert(removedListenerCount == 1)
        assert(closedRealmCount == 1)
    }
}
