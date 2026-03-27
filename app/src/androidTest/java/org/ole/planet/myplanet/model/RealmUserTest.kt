package org.ole.planet.myplanet.model

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.realm.Realm
import io.realm.RealmConfiguration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
            Realm.deleteRealm(realmConfiguration)
        }
    }

    @Test
    fun cleanupDuplicateUsers_removesGuestWhenCouchDbUserExists() {
        val latch = CountDownLatch(1)
        var realm: Realm? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            realm = Realm.getInstance(realmConfiguration)
            realm?.executeTransaction { r ->
                r.createObject(RealmUser::class.java, "1").apply {
                    _id = "org.couchdb.user:testuser"
                    name = "testuser"
                }
                r.createObject(RealmUser::class.java, "2").apply {
                    _id = "guest_testuser"
                    name = "testuser"
                }
            }

            RealmUser.cleanupDuplicateUsers(realm!!) {
                latch.countDown()
            }
        }

        latch.await(5, TimeUnit.SECONDS)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            realm?.close()

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
        var realm: Realm? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            realm = Realm.getInstance(realmConfiguration)
            realm?.executeTransaction { r ->
                r.createObject(RealmUser::class.java, "1").apply {
                    _id = "guest_testuser"
                    name = "testuser"
                }
                r.createObject(RealmUser::class.java, "2").apply {
                    _id = "org.couchdb.user:testuser"
                    name = "testuser"
                }
            }

            RealmUser.cleanupDuplicateUsers(realm!!) {
                latch.countDown()
            }
        }

        latch.await(5, TimeUnit.SECONDS)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            realm?.close()

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
        var realm: Realm? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            realm = Realm.getInstance(realmConfiguration)
            realm?.executeTransaction { r ->
                r.createObject(RealmUser::class.java, "1").apply {
                    _id = "guest_testuser1"
                    name = "testuser1"
                }
                r.createObject(RealmUser::class.java, "2").apply {
                    _id = "org.couchdb.user:testuser2"
                    name = "testuser2"
                }
            }

            RealmUser.cleanupDuplicateUsers(realm!!) {
                latch.countDown()
            }
        }

        latch.await(5, TimeUnit.SECONDS)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            realm?.close()

            val verifyRealm = Realm.getInstance(realmConfiguration)
            val users = verifyRealm.where(RealmUser::class.java).findAll()
            assertEquals(2, users.size)
            verifyRealm.close()
        }
    }
}
