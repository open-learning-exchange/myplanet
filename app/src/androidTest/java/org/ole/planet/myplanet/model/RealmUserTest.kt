package org.ole.planet.myplanet.model

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.realm.Realm
import io.realm.RealmConfiguration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealmUserTest {

    private lateinit var realmConfiguration: RealmConfiguration

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            Realm.init(ApplicationProvider.getApplicationContext())
            realmConfiguration = RealmConfiguration.Builder()
                .name("test-realm-user")
                .inMemory()
                .allowWritesOnUiThread(true)
                .allowQueriesOnUiThread(true)
                .schemaVersion(1)
                .build()
            Realm.setDefaultConfiguration(realmConfiguration)
        }
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val realm = Realm.getInstance(realmConfiguration)
            realm.executeTransaction { it.deleteAll() }
            realm.close()
        }
    }

    @Test
    fun cleanupDuplicateUsers_removesGuestWhenCouchDbUserExists() {
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val realm = Realm.getInstance(realmConfiguration)
            realm.executeTransaction { r ->
                r.createObject(RealmUser::class.java, "1").apply {
                    _id = "org.couchdb.user:testuser"
                    name = "testuser"
                }
                r.createObject(RealmUser::class.java, "2").apply {
                    _id = "guest_testuser"
                    name = "testuser"
                }
            }

            RealmUser.cleanupDuplicateUsers(realm) {
                latch.countDown()
                realm.close() // Close the realm after async transaction finishes
            }
        }

        latch.await(5, TimeUnit.SECONDS)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val verifyRealm = Realm.getInstance(realmConfiguration)
            val users = verifyRealm.where(RealmUser::class.java).findAll()
            assertEquals(1, users.size)
            assertEquals("org.couchdb.user:testuser", users.first()!!._id)
            verifyRealm.close()
        }
    }

    @Test
    fun cleanupDuplicateUsers_keepsCouchDbUserWhenGuestExists() {
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val realm = Realm.getInstance(realmConfiguration)
            realm.executeTransaction { r ->
                r.createObject(RealmUser::class.java, "1").apply {
                    _id = "guest_testuser"
                    name = "testuser"
                }
                r.createObject(RealmUser::class.java, "2").apply {
                    _id = "org.couchdb.user:testuser"
                    name = "testuser"
                }
            }

            RealmUser.cleanupDuplicateUsers(realm) {
                latch.countDown()
                realm.close()
            }
        }

        latch.await(5, TimeUnit.SECONDS)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val verifyRealm = Realm.getInstance(realmConfiguration)
            val users = verifyRealm.where(RealmUser::class.java).findAll()
            assertEquals(1, users.size)
            assertEquals("org.couchdb.user:testuser", users.first()!!._id)
            verifyRealm.close()
        }
    }

    @Test
    fun cleanupDuplicateUsers_keepsAllUsersWhenNoDuplicates() {
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val realm = Realm.getInstance(realmConfiguration)
            realm.executeTransaction { r ->
                r.createObject(RealmUser::class.java, "1").apply {
                    _id = "guest_testuser1"
                    name = "testuser1"
                }
                r.createObject(RealmUser::class.java, "2").apply {
                    _id = "org.couchdb.user:testuser2"
                    name = "testuser2"
                }
            }

            RealmUser.cleanupDuplicateUsers(realm) {
                latch.countDown()
                realm.close()
            }
        }

        latch.await(5, TimeUnit.SECONDS)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val verifyRealm = Realm.getInstance(realmConfiguration)
            val users = verifyRealm.where(RealmUser::class.java).findAll()
            assertEquals(2, users.size)
            verifyRealm.close()
        }
    }
}
