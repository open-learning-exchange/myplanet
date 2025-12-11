package org.ole.planet.myplanet.repository

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.RealmClass
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference

@RealmClass
open class TestRealmObject : RealmObject() {
    var id: String = ""
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class RealmRepositoryTest {
    @Test
    fun `queryListFlow producer runs on a background thread`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseService = DatabaseService(context)
        val producerThread = AtomicReference<Thread>()

        val repository = object : RealmRepository(databaseService) {
            override suspend fun <T> withRealmAsync(operation: (Realm) -> T): T {
                producerThread.set(Thread.currentThread())
                return super.withRealmAsync(operation)
            }
        }

        val flow = repository.queryListFlow(TestRealmObject::class.java)
        flow.first()

        assertNotEquals(
            "Producer should not run on the main thread",
            Looper.getMainLooper().thread,
            producerThread.get()
        )
    }
}
