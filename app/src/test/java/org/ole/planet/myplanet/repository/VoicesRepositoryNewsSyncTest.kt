package org.ole.planet.myplanet.repository

import android.app.Application
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.data.room.dao.MyLibraryDao
import org.ole.planet.myplanet.data.room.dao.NewsDao
import org.ole.planet.myplanet.data.room.dao.TeamNotificationDao
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Real in-memory Room coverage for the News sync insert path
 * ([VoicesRepositoryImpl.insertNewsList]) — the highest-traffic Voices sync write. The rest of the
 * suite mocks the DAO, so this is the only test that exercises the JSON->entity mapping, the
 * batched existing-row dedup (the former N+1), and the top-level/reply queries the UI relies on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26])
class VoicesRepositoryNewsSyncTest {

    private lateinit var db: AppDatabase
    private lateinit var newsDao: NewsDao
    private lateinit var repository: VoicesRepositoryImpl
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)

    private fun messageDoc(id: String, message: String, userName: String = "Alice"): JsonObject =
        JsonObject().apply {
            addProperty("_id", id)
            addProperty("_rev", "1-$id")
            addProperty("docType", "message")
            addProperty("message", message)
            addProperty("time", 1000L)
            add("user", JsonObject().apply {
                addProperty("_id", "org.couchdb.user:$userName")
                addProperty("name", userName)
            })
        }

    private fun replyDoc(id: String, replyTo: String, message: String): JsonObject =
        messageDoc(id, message).apply { addProperty("replyTo", replyTo) }

    @Before
    fun setUp() {
        mockkObject(UrlUtils)
        every { UrlUtils.getUrl() } returns "http://test.local"
        // Non-null String return => a relaxed mock yields "" and gson.fromJson("") returns null,
        // NPE-ing saveConcatenatedLinksToPrefs. Return null so it takes the empty-set branch.
        every { sharedPrefManager.getConcatenatedLinks() } returns null

        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        newsDao = db.newsDao()

        repository = VoicesRepositoryImpl(
            mockk<DispatcherProvider>(relaxed = true),
            Gson(),
            sharedPrefManager,
            mockk<dagger.Lazy<UserRepository>>(relaxed = true),
            mockk<TeamNotificationDao>(relaxed = true),
            newsDao,
            mockk<MyLibraryDao>(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        db.close()
        unmockkObject(UrlUtils)
    }

    @Test
    fun `insertNewsList persists mapped news and drives top-level and reply queries`() = runBlocking {
        repository.insertNewsList(
            listOf(
                messageDoc("n1", "Hello world"),
                messageDoc("n2", "Second post", userName = "Bob"),
                replyDoc("r1", replyTo = "n1", message = "A reply"),
            )
        )

        val n1 = newsDao.getByUnderscoreId("n1")
        assertNotNull(n1)
        assertEquals("Hello world", n1?.message)
        assertEquals("message", n1?.docType)
        assertEquals("Bob", newsDao.getByUnderscoreId("n2")?.userName)

        // Top-level messages exclude the reply; the reply is reachable via getReplies.
        assertEquals(setOf("n1", "n2"), newsDao.getTopLevelMessages().map { it._id }.toSet())
        assertEquals(listOf("r1"), newsDao.getReplies("n1").map { it._id })
    }

    @Test
    fun `insertNewsList updates existing rows in place instead of duplicating`() = runBlocking {
        repository.insertNewsList(listOf(messageDoc("n1", "Original")))
        assertEquals("Original", newsDao.getByUnderscoreId("n1")?.message)

        // Re-sync the same _id with a new message: the batched dedup must update the same row.
        repository.insertNewsList(listOf(messageDoc("n1", "Edited")))

        assertEquals("Edited", newsDao.getByUnderscoreId("n1")?.message)
        assertEquals(1, newsDao.getAll().size)
    }
}
