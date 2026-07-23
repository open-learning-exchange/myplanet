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
import org.ole.planet.myplanet.data.room.dao.MyLibraryDao
import org.ole.planet.myplanet.data.room.dao.NewsDao
import org.ole.planet.myplanet.data.room.dao.TeamNotificationDao
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
    private val teamNotificationDao: TeamNotificationDao = mockk(relaxed = true)
    private val newsDao: NewsDao = mockk(relaxed = true)
    private val myLibraryDao: MyLibraryDao = mockk(relaxed = true)

    private fun newRepository(gsonInstance: Gson): VoicesRepositoryImpl {
        return spyk(
            VoicesRepositoryImpl(
                dispatcherProvider,
                gsonInstance,
                sharedPrefManager,
                dagger.Lazy { userRepository },
                teamNotificationDao,
                newsDao,
                myLibraryDao
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
        every { newsDao.getTopLevelFlow() } returns flowOf(emptyList())

        val flow = repository.getDiscussionsByTeamIdFlow("testTeam")
        val result = flow.toList()

        assertNotNull(result)
        io.mockk.verify { dispatcherProvider.default }
    }

    @Test
    fun `getCommunityVisibleNews filters correctly based on viewableBy and viewIn`() = testScope.runTest {
        val repoWithRealGson = newRepository(Gson())

        val news1 = News().apply {
            viewableBy = "community"
            viewIn = null
        }
        val news2 = News().apply {
            viewableBy = "other"
            viewIn = "[{\"_id\":\"user1\"}]"
        }
        val news3 = News().apply {
            viewableBy = "other"
            viewIn = "[{\"_id\":\"user2\"}]"
        }
        coEvery { newsDao.getTopLevelMessages() } returns listOf(news1, news2, news3)

        val result = repoWithRealGson.getCommunityVisibleNews("user1")

        assertEquals(2, result.size)
        assertEquals("community", result[0].viewableBy)
        assertEquals("[{\"_id\":\"user1\"}]", result[1].viewIn)
    }

    @Test
    fun `getNewsByTeamId filters correctly based on viewableBy and viewIn`() = testScope.runTest {
        val news1 = News().apply {
            viewableBy = "teams"
            viewableId = "team1"
        }
        val news2 = News().apply {
            viewableBy = "other"
            viewIn = "[{\"_id\":\"team1\"}]"
        }
        val news3 = News().apply {
            viewableBy = "other"
            viewIn = "[{\"_id\":\"team2\"}]"
        }
        coEvery { newsDao.getTopLevel() } returns listOf(news1, news2, news3)

        val result = repository.getNewsByTeamId("team1")

        assertEquals(2, result.size)
        assertEquals("teams", result[0].viewableBy)
        assertEquals("[{\"_id\":\"team1\"}]", result[1].viewIn)
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
        coEvery { newsDao.getTopLevel() } returns listOf(news1, news2)

        val result = repository.getFilteredNews("team1")

        assertEquals(1, result.size)
        assertEquals("teams", result[0].viewableBy)
    }

    @Test
    fun `deleteNews recursively deletes replies`() = testScope.runTest {
        val reply1 = News().apply { id = "reply1_id" }
        val reply2 = News().apply { id = "reply2_id" }

        coEvery { newsDao.getDirectReplies("newsId") } returns listOf(reply1)
        coEvery { newsDao.getDirectReplies("reply1_id") } returns listOf(reply2)
        coEvery { newsDao.getDirectReplies("reply2_id") } returns emptyList()

        repository.deleteNews("newsId")

        val idsSlot = slot<List<String>>()
        coVerify(exactly = 1) { newsDao.deleteByIds(capture(idsSlot)) }
        assertEquals(listOf("newsId", "reply1_id", "reply2_id"), idsSlot.captured)
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
    fun `getUserById delegates to userRepository`() = testScope.runTest {
        val testUserId = "test_user_123"
        val mockUser = mockk<UserEntity>()

        coEvery { userRepository.getUserById(testUserId) } returns mockUser

        val result = repository.getUserById(testUserId)

        assertEquals(mockUser, result)
    }
}
