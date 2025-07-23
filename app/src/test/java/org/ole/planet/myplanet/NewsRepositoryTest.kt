import android.app.Application
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmList
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.di.NewsRepository
import org.ole.planet.myplanet.di.NewsRepositoryImpl
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.datamanager.ApiInterface

class DummyApi : ApiInterface {
    override fun downloadFile(header: String?, fileUrl: String?) = throw NotImplementedError()
    override fun getDocuments(header: String?, url: String?) = throw NotImplementedError()
    override fun getJsonObject(header: String?, url: String?) = throw NotImplementedError()
    override fun findDocs(header: String?, c: String?, url: String?, s: com.google.gson.JsonObject?) = throw NotImplementedError()
    override fun postDoc(header: String?, c: String?, url: String?, s: com.google.gson.JsonObject?) = throw NotImplementedError()
    override fun uploadResource(headerMap: Map<String, String>, url: String?, body: okhttp3.RequestBody?) = throw NotImplementedError()
    override fun putDoc(header: String?, c: String?, url: String?, s: com.google.gson.JsonObject?) = throw NotImplementedError()
    override fun checkVersion(serverUrl: String?) = throw NotImplementedError()
    override fun getApkVersion(url: String?) = throw NotImplementedError()
    override fun healthAccess(url: String?) = throw NotImplementedError()
    override fun getChecksum(url: String?) = throw NotImplementedError()
    override fun isPlanetAvailable(serverUrl: String?) = throw NotImplementedError()
    override fun chatGpt(url: String?, requestBody: okhttp3.RequestBody?) = throw NotImplementedError()
    override fun checkAiProviders(url: String?) = throw NotImplementedError()
    override fun getConfiguration(url: String?) = throw NotImplementedError()
}

class NewsRepositoryTest {
    private lateinit var realm: Realm
    private lateinit var repository: NewsRepository

    @Before
    fun setUp() {
        Realm.init(Application())
        val config = RealmConfiguration.Builder()
            .name("test.realm")
            .deleteRealmIfMigrationNeeded()
            .allowWritesOnUiThread(true)
            .build()
        realm = Realm.getInstance(config)
        repository = NewsRepositoryImpl(realm, DummyApi())
    }

    @After
    fun tearDown() {
        realm.close()
    }

    @Test
    fun testSaveAndRetrieveReply() {
        realm.executeTransaction { r ->
            val user = r.createObject(RealmUserModel::class.java, "u1")
            user.name = "Test"
            val parent = r.createObject(RealmNews::class.java, "p1")
            parent.message = "parent"
            parent.docType = "message"
            parent.time = 1
        }
        val user = realm.where(RealmUserModel::class.java).findFirst()
        val parent = repository.getNewsById("p1")
        val map = hashMapOf<String?, String>("message" to "reply", "replyTo" to "p1")
        repository.createNews(map, user, RealmList(), true)
        val replies = repository.getReplies("p1")
        assert(replies.size == 1)
    }
}
