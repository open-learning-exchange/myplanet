package org.ole.planet.myplanet.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.realm.Realm
import io.realm.RealmConfiguration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class DatabaseServiceTest {

    private lateinit var context: Context
    private lateinit var databaseService: DatabaseService

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        Realm.init(context)
        val testConfig = RealmConfiguration.Builder()
            .inMemory()
            .name("test-realm")
            .build()
        Realm.setDefaultConfiguration(testConfig)

        databaseService = DatabaseService(context)
    }

    @After
    fun tearDown() {
        val realm = Realm.getDefaultInstance()
        realm.executeTransaction { it.deleteAll() }
        realm.close()
    }

    @Test
    fun `withRealmAsync executes operation successfully and returns value`() = runTest {
        val result = databaseService.withRealmAsync { realm ->
            assertFalse(realm.isClosed)
            "success_async"
        }

        assertEquals("success_async", result)
    }

    @Test
    fun `executeTransactionAsync executes transaction block successfully`() = runTest {
        var transactionExecuted = false

        databaseService.executeTransactionAsync { realm ->
            assertTrue(realm.isInTransaction)
            transactionExecuted = true
        }

        assertTrue(transactionExecuted, "Transaction block was not executed")
    }
}
