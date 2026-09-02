package org.ole.planet.myplanet.repository

import android.text.TextUtils
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.NewsDao
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DispatcherProvider

@ExperimentalCoroutinesApi
class VoicesRepositoryImplTest {

    private lateinit var repository: VoicesRepositoryImpl
    private val dispatcherProvider: DispatcherProvider = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val gson: Gson = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val newsDao: NewsDao = mockk(relaxed = true)
    private val newsLogDao: org.ole.planet.myplanet.data.room.dao.NewsLogDao = mockk(relaxed = true)

    private fun newRepository(gsonInstance: Gson): VoicesRepositoryImpl {
        return spyk(
            VoicesRepositoryImpl(
                dispatcherProvider,
                gsonInstance,
                Gson(),
                sharedPrefManager,
                newsDao,
                newsLogDao
            ),
            recordPrivateCalls = true
        )
    }

    @Before
    fun setUp() {
        every { dispatcherProvider.default } returns testDispatcher
        repository = newRepository(gson)
    }

    @Test
    fun getCommunityNews_uses_dispatcherProvider_default() = testScope.runTest {
        every { newsDao.getTopLevelMessagesFlow() } returns flowOf(emptyList())

        val flow = repository.getCommunityNews("testUser")
        val result = flow.toList()

        assertNotNull(result)
        io.mockk.verify { dispatcherProvider.default }
    }

    @Test
    fun `getCommunityVoiceDateCount delegates to count query when userId is null`() = testScope.runTest {
        coEvery { newsDao.countDistinctCommunityVoiceDates(1000L, 2000L) } returns 3

        val count = repository.getCommunityVoiceDateCount(1000L, 2000L, null)

        assertEquals(3, count)
        coVerify(exactly = 1) { newsDao.countDistinctCommunityVoiceDates(1000L, 2000L) }
        coVerify(exactly = 0) { newsDao.countDistinctCommunityVoiceDatesForUser(any(), any(), any()) }
    }

    @Test
    fun `getCommunityVoiceDateCount delegates to user-scoped count query when userId is non-null`() = testScope.runTest {
        coEvery { newsDao.countDistinctCommunityVoiceDatesForUser(1000L, 2000L, "user1") } returns 5

        val count = repository.getCommunityVoiceDateCount(1000L, 2000L, "user1")

        assertEquals(5, count)
        coVerify(exactly = 1) { newsDao.countDistinctCommunityVoiceDatesForUser(1000L, 2000L, "user1") }
        coVerify(exactly = 0) { newsDao.countDistinctCommunityVoiceDates(any(), any()) }
    }

    @Test
    fun `getNewsForUpload filters guest users and correctly serializes payloads`() = testScope.runTest {
        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any()) } answers { firstArg<CharSequence?>().isNullOrEmpty() }

        val repoWithRealGson = newRepository(Gson())

        val guestNews = News().apply {
            id = "guest_news_id"
            userId = "guest_123"
        }
        val validNews = News().apply {
            id = "valid_news_id"
            _id = "valid_news_id"
            userId = "user_123"
            message = "Hello World"
            user = "{}"
            conversations = "[]"
        }
        coEvery { newsDao.getAll() } returns listOf(guestNews, validNews)

        val result = repoWithRealGson.getNewsForUpload()

        assertEquals(1, result.size)
        assertEquals("valid_news_id", result[0].id)
        assertEquals("Hello World", result[0].message)
        assertEquals("Hello World", result[0].newsJson.get("message").asString)
        assertNotNull(result[0].newsJson.get("user"))
    }

    @Test
    fun getDiscussionsByTeamIdFlow_uses_dispatcherProvider_default() = testScope.runTest {
        every { newsDao.getTopLevelByTeamFlow(any(), any()) } returns flowOf(emptyList())

        val flow = repository.getDiscussionsByTeamIdFlow("testTeam")
        val result = flow.toList()

        assertNotNull(result)
        io.mockk.verify { dispatcherProvider.default }
    }

    @Test
    fun `getFilteredNews filters top-level posts by team`() = testScope.runTest {
        val news1 = News().apply {
            viewableBy = "teams"
            viewableId = "team1"
        }
        val news2 = News().apply {
            viewableBy = "other"
            viewIn = "[{\"_id\":\"team2\"}]"
        }
        coEvery { newsDao.getTopLevelByTeam(any(), any()) } returns listOf(news1)

        val result = repository.getFilteredNews("team1")

        assertEquals(1, result.size)
        assertEquals("teams", result[0].viewableBy)
    }

    @Test
    fun `addLabel appends label and upserts`() = testScope.runTest {
        val news = News().apply {
            id = "newsId"
            labels = listOf("existing")
        }
        coEvery { newsDao.getById("newsId") } returns news

        repository.addLabel("newsId", "testLabel")

        val slot = slot<News>()
        coVerify(exactly = 1) { newsDao.upsert(capture(slot)) }
        assertTrue(slot.captured.labels!!.contains("testLabel"))
        assertTrue(slot.captured.labels!!.contains("existing"))
    }

    @Test
    fun `removeLabel drops label and upserts`() = testScope.runTest {
        val news = News().apply {
            id = "newsId"
            labels = listOf("testLabel", "keep")
        }
        coEvery { newsDao.getById("newsId") } returns news

        repository.removeLabel("newsId", "testLabel")

        val slot = slot<News>()
        coVerify(exactly = 1) { newsDao.upsert(capture(slot)) }
        assertEquals(listOf("keep"), slot.captured.labels)
    }

    @Test
    fun `postReply sets replyTo to the parent's local id, not its server _id`() = testScope.runTest {
        val repoWithRealGson = newRepository(Gson())
        val parentNews = News().apply {
            id = "local-uuid-1234"
            _id = "server-doc-id-5678"
        }
        val currentUser = UserEntity()

        repoWithRealGson.postReply("Hello reply", parentNews, currentUser, null)

        val slot = slot<News>()
        coVerify(exactly = 1) { newsDao.upsert(capture(slot)) }
        assertEquals("local-uuid-1234", slot.captured.replyTo)
    }

    @Test
    fun `deletePost from community unshares shared enterprise post without deleting row`() = testScope.runTest {
        val repoWithRealGson = newRepository(Gson())
        val sharedNews = News().apply {
            id = "shared_news_123"
            sharedBy = "user_1"
            viewIn = "[{\"_id\":\"team_123\",\"section\":\"teams\",\"name\":\"Enterprise A\"},{\"section\":\"community\",\"_id\":\"planet@parent\",\"sharedDate\":123456789}]"
        }
        coEvery { newsDao.getById("shared_news_123") } returns sharedNews

        repoWithRealGson.deletePost("shared_news_123", "")

        val slot = slot<News>()
        coVerify(exactly = 1) { newsDao.upsert(capture(slot)) }
        coVerify(exactly = 0) { newsDao.deleteByIds(any()) }

        val updatedNews = slot.captured
        assertEquals("", updatedNews.sharedBy)
        assertEquals("[{\"_id\":\"team_123\",\"section\":\"teams\",\"name\":\"Enterprise A\"}]", updatedNews.viewIn)
    }

    @Test
    fun `deletePost from team deletes post and replies completely`() = testScope.runTest {
        val repoWithRealGson = newRepository(Gson())
        val teamNews = News().apply {
            id = "team_news_123"
            viewIn = "[{\"_id\":\"team_123\",\"section\":\"teams\",\"name\":\"Enterprise A\"},{\"section\":\"community\",\"_id\":\"planet@parent\",\"sharedDate\":123456789}]"
        }
        coEvery { newsDao.getById("team_news_123") } returns teamNews
        coEvery { newsDao.getNewsAndRepliesIds("team_news_123") } returns listOf("team_news_123")

        repoWithRealGson.deletePost("team_news_123", "Enterprise A")

        val idsSlot = slot<List<String>>()
        coVerify(exactly = 1) { newsDao.deleteByIds(capture(idsSlot)) }
        assertEquals(listOf("team_news_123"), idsSlot.captured)
        coVerify(exactly = 0) { newsDao.upsert(any()) }
    }

    @Test
    fun `deletePost from community deletes direct community post`() = testScope.runTest {
        val repoWithRealGson = newRepository(Gson())
        val communityNews = News().apply {
            id = "comm_news_123"
            viewIn = "[{\"_id\":\"planet@parent\",\"section\":\"community\",\"name\":\"\"}]"
        }
        coEvery { newsDao.getById("comm_news_123") } returns communityNews
        coEvery { newsDao.getNewsAndRepliesIds("comm_news_123") } returns listOf("comm_news_123")

        repoWithRealGson.deletePost("comm_news_123", "")

        val idsSlot = slot<List<String>>()
        coVerify(exactly = 1) { newsDao.deleteByIds(capture(idsSlot)) }
        assertEquals(listOf("comm_news_123"), idsSlot.captured)
        coVerify(exactly = 0) { newsDao.upsert(any()) }
    }
}
