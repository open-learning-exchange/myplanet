package org.ole.planet.myplanet.ui.dashboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.realm.Realm
import io.realm.RealmConfiguration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLife

@RunWith(AndroidJUnit4::class)
class BaseDashboardFragmentRealmTest {

    private lateinit var context: Context
    private lateinit var databaseService: DatabaseService
    private lateinit var realmConfig: RealmConfiguration

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseService = DatabaseService(context)
        realmConfig = RealmConfiguration.Builder()
            .name("setUpMyLifeTest.realm")
            .inMemory()
            .allowWritesOnUiThread(true)
            .build()
        Realm.setDefaultConfiguration(realmConfig)
    }

    @After
    fun tearDown() {
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction { it.deleteAll() }
        }
        Realm.deleteRealm(realmConfig)
    }

    @Test
    fun setUpMyLifeDoesNotLeakRealmInstances() {
        val fragment = BaseDashboardFragment()

        val templates = listOf(
            RealmMyLife("img1", "user", "Title 1"),
            RealmMyLife("img2", "user", "Title 2")
        )

        val initialCount = Realm.getLocalInstanceCount(realmConfig)

        repeat(3) {
            databaseService.withRealm { realm ->
                fragment.populateMyLifeIfEmpty(realm, "user", templates)
            }
        }

        val finalCount = Realm.getLocalInstanceCount(realmConfig)
        assertEquals(initialCount, finalCount)

        val insertedCount = databaseService.withRealm { realm ->
            realm.where(RealmMyLife::class.java).equalTo("userId", "user").count()
        }
        assertTrue(insertedCount > 0)
    }
}
