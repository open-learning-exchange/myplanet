package org.ole.planet.myplanet.data

import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import io.realm.Realm
import io.realm.RealmConfiguration
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmUserModel

@RunWith(RobolectricTestRunner::class)
class NewsRepositoryTest {
    private lateinit var repository: NewsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        Realm.init(context)
        val config = RealmConfiguration.Builder()
            .inMemory()
            .name("test-realm")
            .allowWritesOnUiThread(true)
            .build()
        Realm.setDefaultConfiguration(config)
        val dbService = DatabaseService(context)
        Realm.setDefaultConfiguration(config)
        repository = NewsRepositoryImpl(dbService)
        val realm = Realm.getDefaultInstance()
        realm.executeTransaction {
            val user = it.createObject(RealmUserModel::class.java, "u1")
            user.name = "Test"
            user.planetCode = "pc"
            user.parentCode = "p"
        }
        realm.close()
    }

    @After
    fun tearDown() {
        Realm.getDefaultInstance().close()
    }

    @Test
    fun testCreateNews() = runBlocking {
        val realm = Realm.getDefaultInstance()
        val user = realm.where(RealmUserModel::class.java).findFirst()
        val map = hashMapOf<String?, String>()
        map["message"] = "hello"
        map["messagePlanetCode"] = "pc"
        map["messageType"] = "sync"
        val news = repository.createNews(map, user, null)
        assertEquals("hello", news.message)
        realm.close()
    }
}
