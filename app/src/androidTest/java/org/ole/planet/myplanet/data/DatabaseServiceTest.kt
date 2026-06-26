package org.ole.planet.myplanet.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.realm.Realm
import io.realm.RealmConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.RealmMeetup

@RunWith(AndroidJUnit4::class)
class DatabaseServiceTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var realmConfiguration: RealmConfiguration

    @Before
    fun setUp() {
        Realm.init(ApplicationProvider.getApplicationContext())
        realmConfiguration = RealmConfiguration.Builder()
            .name("test-realm")
            .inMemory()
            .allowWritesOnUiThread(true)
            .allowQueriesOnUiThread(true)
            .schemaVersion(1)
            .build()
        databaseService = DatabaseService(ApplicationProvider.getApplicationContext(), org.ole.planet.myplanet.utils.DefaultDispatcherProvider(), realmConfiguration)
    }

    @After
    fun tearDown() {
        Realm.deleteRealm(realmConfiguration)
    }

    @Test
    fun testWithRealmAsync_successPath_closesRealm() = runBlocking {
                val result = databaseService.withRealmAsync { realm ->
            assertFalse(realm.isClosed)
            "success"
        }
        assertEquals("success", result)
        assertTrue(Realm.getGlobalInstanceCount(realmConfiguration) == 0)
    }

    @Test
    fun testWithRealmAsync_exceptionPath_closesRealm() = runBlocking {
                val exception = runCatching {
            databaseService.withRealmAsync { realm ->
                    assertFalse(realm.isClosed)
                throw RuntimeException("Test exception")
            }
        }.exceptionOrNull()

        assertNotNull(exception)
        assertEquals("Test exception", exception?.message)
        assertTrue(Realm.getGlobalInstanceCount(realmConfiguration) == 0)
    }

    @Test
    fun testExecuteTransactionAsync_commitsData() = runBlocking {
                databaseService.executeTransactionAsync { realm ->
            val meetup = realm.createObject(RealmMeetup::class.java, "test-id")
            meetup.meetupId = "test-id"
            meetup.title = "Test Meetup"
        }

        databaseService.withRealmAsync { realm ->
            val meetup = realm.where(RealmMeetup::class.java).equalTo("meetupId", "test-id").findFirst()
            assertNotNull(meetup)
            assertEquals("Test Meetup", meetup?.title)
            assertNotNull(capturedRealm)
            assertTrue(Realm.getGlobalInstanceCount(realmConfiguration) == 0)
        }
    }

    @Test
    fun testExecuteTransactionAsync_exceptionPath_closesRealm() = runBlocking {
                val exception = runCatching {
            databaseService.executeTransactionAsync { realm ->
                    assertFalse(realm.isClosed)
                throw RuntimeException("Test exception in transaction")
            }
        }.exceptionOrNull()

        assertNotNull(exception)
        assertEquals("Test exception in transaction", exception?.message)
        assertTrue(Realm.getGlobalInstanceCount(realmConfiguration) == 0)
    }

    @Test
    fun testConcurrency_doesNotLeaveRealmsOpen() = runBlocking {
        val initialGlobalCount = Realm.getGlobalInstanceCount(realmConfiguration)

        val deferreds = (1..50).map { i ->
            async(Dispatchers.Default) {
                databaseService.withRealmAsync { realm ->
                    assertFalse(realm.isClosed)
                    i
                }
            }
        }

        val results = deferreds.awaitAll()
        assertEquals(50, results.size)

        val finalGlobalCount = Realm.getGlobalInstanceCount(realmConfiguration)
        assertEquals("Global instance count should be unchanged after concurrent ops", initialGlobalCount, finalGlobalCount)
    }
}
