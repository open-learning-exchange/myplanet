package org.ole.planet.myplanet.model

import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.Lazy
import io.mockk.mockk
import io.realm.Realm
import io.realm.RealmConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.repository.UserRepositoryImpl
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UploadToShelfService
import org.ole.planet.myplanet.utils.DispatcherProvider

@RunWith(AndroidJUnit4::class)
class RealmUserTest {

    private lateinit var realmConfiguration: RealmConfiguration
    private lateinit var userRepository: UserRepositoryImpl
    private lateinit var databaseService: DatabaseService

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

        databaseService = object : DatabaseService {
            override val realm: Realm
                get() = Realm.getInstance(realmConfiguration)

            override fun executeTransaction(transaction: Realm.Transaction) {
                realm.executeTransaction(transaction)
            }

            override fun executeTransactionAsync(
                transaction: Realm.Transaction,
                onSuccess: Realm.Transaction.OnSuccess,
                onError: Realm.Transaction.OnError
            ) {
                realm.executeTransactionAsync(transaction, onSuccess, onError)
            }
        }

        val mockSettings = mockk<SharedPreferences>(relaxed = true)
        val mockSharedPrefManager = mockk<SharedPrefManager>(relaxed = true)
        val mockApiInterface = mockk<ApiInterface>(relaxed = true)
        val mockUploadToShelfService = mockk<Lazy<UploadToShelfService>>(relaxed = true)
        val mockContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val mockConfigurationsRepository = mockk<ConfigurationsRepository>(relaxed = true)
        val mockAppScope = CoroutineScope(Dispatchers.Unconfined)
        val mockDispatcherProvider = mockk<DispatcherProvider>(relaxed = true)

        userRepository = UserRepositoryImpl(
            databaseService,
            Dispatchers.Unconfined,
            mockSettings,
            mockSharedPrefManager,
            mockApiInterface,
            mockUploadToShelfService,
            mockContext,
            mockConfigurationsRepository,
            mockAppScope,
            mockDispatcherProvider
        )
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
    fun cleanupDuplicateUsers_removesGuestWhenCouchDbUserExists() = runBlocking {
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
            realm.close()
        }

        userRepository.cleanupDuplicateUsers()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val verifyRealm = Realm.getInstance(realmConfiguration)
            val users = verifyRealm.where(RealmUser::class.java).findAll()
            assertEquals(1, users.size)
            assertEquals("org.couchdb.user:testuser", users.first()!!._id)
            verifyRealm.close()
        }
    }

    @Test
    fun cleanupDuplicateUsers_keepsCouchDbUserWhenGuestExists() = runBlocking {
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
            realm.close()
        }

        userRepository.cleanupDuplicateUsers()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val verifyRealm = Realm.getInstance(realmConfiguration)
            val users = verifyRealm.where(RealmUser::class.java).findAll()
            assertEquals(1, users.size)
            assertEquals("org.couchdb.user:testuser", users.first()!!._id)
            verifyRealm.close()
        }
    }

    @Test
    fun cleanupDuplicateUsers_keepsAllUsersWhenNoDuplicates() = runBlocking {
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
            realm.close()
        }

        userRepository.cleanupDuplicateUsers()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val verifyRealm = Realm.getInstance(realmConfiguration)
            val users = verifyRealm.where(RealmUser::class.java).findAll()
            assertEquals(2, users.size)
            verifyRealm.close()
        }
    }
}
