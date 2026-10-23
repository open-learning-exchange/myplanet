package org.ole.planet.myplanet.repository

import android.app.Application
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.data.room.dao.NewsDao
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.NetworkUtils
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
@Config(application = Application::class)
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
        mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "android-id"
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
            Gson(),
            sharedPrefManager,
            newsDao,
            mockk<org.ole.planet.myplanet.data.room.dao.NewsLogDao>(relaxed = true),
            db.removedLogDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
        unmockkObject(UrlUtils, NetworkUtils)
    }

    @Test
    fun `a deleted synced post is queued for server deletion and not re-added by the next pull`() = runBlocking {
        repository.insertNewsList(listOf(messageDoc("n1", "Hello")))
        val localId = newsDao.getByUnderscoreId("n1")!!.id

        repository.deletePost(localId, "")
        repository.insertNewsList(listOf(messageDoc("n1", "Hello")))

        assertNull(newsDao.getByUnderscoreId("n1"))
        assertEquals(listOf("n1"), repository.getPendingNewsDeletions())

        repository.markNewsDeletionsUploaded(listOf("n1"))
        assertTrue(repository.getPendingNewsDeletions().isEmpty())
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
        assertEquals(setOf("n1", "n2"), newsDao.getTopLevelMessagesFlow().first().map { it._id }.toSet())
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

    @Test
    fun `insertNewsList handles duplicate and blank _id values correctly`() = runBlocking {
        val doc1 = messageDoc("n1", "First version")
        val doc1Updated = messageDoc("n1", "Second version")
        val blankDoc = messageDoc("", "Blank id message")

        repository.insertNewsList(listOf(doc1, doc1Updated, blankDoc))

        val allNews = newsDao.getAll()
        assertEquals(2, allNews.size)

        val n1 = newsDao.getByUnderscoreId("n1")
        assertNotNull(n1)
        assertEquals("Second version", n1?.message)

        val blankNews = newsDao.getByUnderscoreId("")
        assertNotNull(blankNews)
        assertEquals("Blank id message", blankNews?.message)
    }
    @Test
    fun `markNewsUploaded keeps the uploaded images and their markdown`() = runBlocking {
        newsDao.upsert(News().apply {
            id = "local-1"
            message = "Hello"
            imageUrls = listOf("{\"imageUrl\":\"/sdcard/new.png\"}")
        })
        val images = JsonArray().apply {
            add(JsonObject().apply { addProperty("resourceId", "new-res"); addProperty("filename", "new.png") })
        }

        repository.markNewsUploaded(listOf(NewsUpdateData("local-1", "server-1", "1-abc", images, message = "Hello\n![](resources/new-res/new.png)")))

        val saved = newsDao.getById("local-1")!!
        assertEquals("server-1", saved._id)
        assertEquals(images, saved.imagesArray)
        assertEquals("Hello\n![](resources/new-res/new.png)", saved.message)
        assertEquals(emptyList<String>(), saved.imageUrls)
    }

    private suspend fun storeLocalPost(id: String, remoteId: String? = null) = newsDao.upsert(News().apply {
        this.id = id
        _id = remoteId
        message = "Post"
        docType = "message"
        user = "{}"
    })

    private suspend fun storeLocalReply(id: String, parentId: String) = newsDao.upsert(News().apply {
        this.id = id
        replyTo = parentId
        message = "Reply"
        docType = "message"
        user = "{}"
    })

    @Test
    fun `a reply to an uploaded local post is sent with the post's server id`() = runBlocking {
        storeLocalPost("local-post", remoteId = "server-post")
        storeLocalReply("local-reply", parentId = "local-post")

        val upload = repository.getNewsForUpload().single { it.id == "local-reply" }

        assertEquals("server-post", upload.newsJson.get("replyTo").asString)
        assertEquals("local-post", newsDao.getById("local-reply")?.replyTo)
    }

    @Test
    fun `a reply waits until its local parent post has uploaded`() = runBlocking {
        storeLocalPost("local-post")
        storeLocalReply("local-reply", parentId = "local-post")

        assertEquals(listOf("local-post"), repository.getNewsForUpload().map { it.id })
    }

    @Test
    fun `a pulled reply to an uploaded local post shows under that post`() = runBlocking {
        storeLocalPost("local-post", remoteId = "server-post")

        repository.insertNewsList(listOf(replyDoc("server-reply", replyTo = "server-post", message = "Hi")))

        assertEquals(listOf("server-reply"), newsDao.getReplies("local-post").map { it.id })
    }

    @Test
    fun `replies to posts from the server keep the server id`() = runBlocking {
        storeLocalPost("server-post", remoteId = "server-post")
        storeLocalReply("local-reply", parentId = "server-post")

        val upload = repository.getNewsForUpload().single { it.id == "local-reply" }

        assertEquals("server-post", upload.newsJson.get("replyTo").asString)
    }

    private fun reactionsOf(vararg entries: Pair<String, List<String>>): JsonObject =
        JsonObject().apply {
            entries.forEach { (emoji, users) -> add(emoji, JsonArray().apply { users.forEach { add(it) } }) }
        }

    @Test
    fun `insertNewsList stores server reactions so they can be displayed`() = runBlocking {
        val serverReactions = mapOf(
            "👍" to listOf("org.couchdb.user:alice", "org.couchdb.user:bob"),
            "🎉" to listOf("org.couchdb.user:carol"),
        )
        val doc = messageDoc("n1", "Hello").apply {
            add("reactions", reactionsOf(*serverReactions.toList().toTypedArray()))
        }

        repository.insertNewsList(listOf(doc))

        assertEquals(serverReactions, newsDao.getByUnderscoreId("n1")?.reactionsMap)
    }

    @Test
    fun `insertNewsList clears reactions removed on the server`() = runBlocking {
        val withReactions = messageDoc("n1", "Hello").apply {
            add("reactions", reactionsOf("👍" to listOf("org.couchdb.user:bob")))
        }
        repository.insertNewsList(listOf(withReactions))

        repository.insertNewsList(listOf(messageDoc("n1", "Hello")))

        assertNull(newsDao.getByUnderscoreId("n1")?.reactions)
    }

    @Test
    fun `toggleReaction is pushed in the upload payload as a reactions object`() = runBlocking {
        repository.insertNewsList(listOf(messageDoc("n1", "Hello")))
        val news = newsDao.getByUnderscoreId("n1")!!

        repository.toggleReaction(news.id, "❤️", "org.couchdb.user:alice")
        mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "test-device"

        val payload = repository.getNewsForUpload().single { it._id == "n1" }.newsJson
        val reactions = payload.getAsJsonObject("reactions")
        assertEquals("org.couchdb.user:alice", reactions.getAsJsonArray("❤️")[0].asString)
    }

    @Test
    fun `pull after a conflicting reaction keeps the unsynced local reaction`() = runBlocking {
        repository.insertNewsList(listOf(messageDoc("n1", "Hello")))
        val id = newsDao.getByUnderscoreId("n1")!!.id
        repository.toggleReaction(id, "👍", "org.couchdb.user:alice")

        // Someone else reacted on Planet meanwhile; the server copy doesn't know about alice yet.
        val serverDoc = messageDoc("n1", "Hello").apply {
            addProperty("_rev", "2-n1")
            add("reactions", reactionsOf("🎉" to listOf("org.couchdb.user:bob")))
        }
        repository.insertNewsList(listOf(serverDoc))

        val merged = newsDao.getByUnderscoreId("n1")!!
        assertEquals("2-n1", merged._rev)
        assertEquals(
            mapOf("🎉" to listOf("org.couchdb.user:bob"), "👍" to listOf("org.couchdb.user:alice")),
            merged.reactionsMap
        )
    }

    @Test
    fun `pending removal survives a pull that still has the reaction`() = runBlocking {
        val doc = messageDoc("n1", "Hello").apply {
            add("reactions", reactionsOf("👍" to listOf("org.couchdb.user:alice")))
        }
        repository.insertNewsList(listOf(doc))
        val id = newsDao.getByUnderscoreId("n1")!!.id
        repository.toggleReaction(id, "👍", "org.couchdb.user:alice")

        repository.insertNewsList(listOf(doc))

        assertTrue(newsDao.getByUnderscoreId("n1")!!.reactionsMap.isEmpty())
    }

    @Test
    fun `markNewsUploaded clears only the pending reactions that were sent`() = runBlocking {
        repository.insertNewsList(listOf(messageDoc("n1", "Hello")))
        val id = newsDao.getByUnderscoreId("n1")!!.id
        repository.toggleReaction(id, "👍", "org.couchdb.user:alice")
        val sent = newsDao.getById(id)!!.pendingReactions
        assertNotNull(sent)

        repository.markNewsUploaded(listOf(NewsUpdateData(id, "n1", "2-n1", JsonArray(), sent)))
        assertNull(newsDao.getById(id)!!.pendingReactions)

        repository.toggleReaction(id, "🎉", "org.couchdb.user:bob")
        // An upload that started before bob's tap must not discard it.
        repository.markNewsUploaded(listOf(NewsUpdateData(id, "n1", "3-n1", JsonArray(), null)))
        assertEquals(mapOf("org.couchdb.user:bob" to "🎉"), newsDao.getById(id)!!.pendingReactionsMap)
    }

    @Test
    fun `insertNewsList accepts reactions encoded as a JSON string`() = runBlocking {
        val doc = messageDoc("n1", "Hello").apply {
            addProperty("reactions", """{"👍":["org.couchdb.user:bob"]}""")
        }

        repository.insertNewsList(listOf(doc))

        assertEquals(mapOf("👍" to listOf("org.couchdb.user:bob")), newsDao.getByUnderscoreId("n1")?.reactionsMap)
    }

    @Test
    fun `getCommunityNews re-emits when a pull changes only reactions`() = runBlocking {
        val dispatchers = mockk<DispatcherProvider> { every { default } returns Dispatchers.Unconfined }
        val flowRepository = VoicesRepositoryImpl(
            dispatchers, Gson(), Gson(), sharedPrefManager, newsDao,
            mockk<org.ole.planet.myplanet.data.room.dao.NewsLogDao>(relaxed = true),
            db.removedLogDao()
        )
        val doc = messageDoc("n1", "Hello").apply { addProperty("viewableBy", "community") }
        repository.insertNewsList(listOf(doc))

        val emissions = Channel<List<News>>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Default) {
            flowRepository.getCommunityNews("").collect { emissions.send(it) }
        }
        try {
            assertNull(withTimeout(5_000.milliseconds) { emissions.receive() }.single().reactions)

            repository.insertNewsList(listOf(doc.deepCopy().apply {
                add("reactions", reactionsOf("👍" to listOf("org.couchdb.user:bob")))
            }))

            val updated = withTimeout(5_000.milliseconds) { emissions.receive() }
            assertEquals(mapOf("👍" to listOf("org.couchdb.user:bob")), updated.single().reactionsMap)
        } finally {
            job.cancel()
        }
    }
}
