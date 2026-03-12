package org.ole.planet.myplanet.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.realm.Realm
import io.realm.RealmConfiguration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DatabaseServiceTest {

    private lateinit var databaseService: DatabaseService

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Realm.init(context)

        val config = RealmConfiguration.Builder()
            .name("test-database.realm")
            .inMemory()
            .allowWritesOnUiThread(true)
            .allowQueriesOnUiThread(true)
            .build()
        Realm.setDefaultConfiguration(config)

        databaseService = DatabaseService(context)
    }

    @After
    fun teardown() {
        val config = Realm.getDefaultConfiguration()
        if (config != null) {
            Realm.deleteRealm(config)
        }
    }

    @Test
    fun withRealmAsyncReturnsCorrectValue() = runTest {
        val expectedResult = "success"

        val actualResult = databaseService.withRealmAsync { realm ->
            assertEquals(false, realm.isClosed)
            expectedResult
        }

        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun withRealmAsyncHandlesExceptionInBlock() = runTest {
        var exceptionThrown = false
        try {
            databaseService.withRealmAsync<Unit> { realm ->
                throw RuntimeException("Test exception")
            }
        } catch (e: RuntimeException) {
            exceptionThrown = true
            assertEquals("Test exception", e.message)
        }

        assertTrue(exceptionThrown)
    }

    @Test
    fun withRealmReturnsCorrectValue() {
        val expectedResult = "success"

        val actualResult = databaseService.withRealm { realm ->
            assertEquals(false, realm.isClosed)
            expectedResult
        }

        assertEquals(expectedResult, actualResult)
    }
}
