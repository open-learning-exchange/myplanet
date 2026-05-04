package org.ole.planet.myplanet.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.realm.Realm
import io.realm.RealmConfiguration
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.RealmMeetup

@RunWith(AndroidJUnit4::class)
class DatabaseServiceClearAllTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var realmConfiguration: RealmConfiguration

    @Before
    fun setUp() {
        Realm.init(ApplicationProvider.getApplicationContext())
        realmConfiguration = RealmConfiguration.Builder()
            .name("test-clearall-realm")
            .inMemory()
            .allowWritesOnUiThread(true)
            .allowQueriesOnUiThread(true)
            .schemaVersion(1)
            .build()
        Realm.setDefaultConfiguration(realmConfiguration)

        databaseService = DatabaseService(ApplicationProvider.getApplicationContext(), org.ole.planet.myplanet.utils.DefaultDispatcherProvider())
    }

    @After
    fun tearDown() {
        Realm.deleteRealm(realmConfiguration)
    }

    @Test
    fun testClearAll_removesAllData() = runBlocking {
        // Arrange
        databaseService.executeTransactionAsync { realm ->
            val meetup1 = realm.createObject(RealmMeetup::class.java, "test-id-1")
            meetup1.title = "Test Meetup 1"

            val meetup2 = realm.createObject(RealmMeetup::class.java, "test-id-2")
            meetup2.title = "Test Meetup 2"
        }

        // Verify inserted
        databaseService.withRealmAsync { realm ->
            val count = realm.where(RealmMeetup::class.java).count()
            assertEquals(2L, count)
        }

        // Act
        databaseService.clearAll()

        // Assert
        databaseService.withRealmAsync { realm ->
            val count = realm.where(RealmMeetup::class.java).count()
            assertEquals(0L, count)
        }
    }
}
