package org.ole.planet.myplanet.repository

import android.text.TextUtils
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.MainDispatcherRule

@ExperimentalCoroutinesApi
class VoicesRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: VoicesRepositoryImpl
    private val dispatcherProvider: DispatcherProvider = mockk(relaxed = true)
    private val testDispatcher = mainDispatcherRule.testDispatcher
    private val testScope = TestScope(testDispatcher)
    private val databaseService: DatabaseService = mockk(relaxed = true)
    private val gson: Gson = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        every { dispatcherProvider.default } returns testDispatcher
        repository = spyk(VoicesRepositoryImpl(
            databaseService,
            mainDispatcherRule.testDispatcher,
            dispatcherProvider,
            gson,
            sharedPrefManager,
            dagger.Lazy { userRepository }
        ), recordPrivateCalls = true)
    }

    @Test
    fun getCommunityNews_uses_dispatcherProvider_default() = testScope.runTest {
        coEvery { repository["queryListFlow"](RealmNews::class.java, any<Function1<*, *>>()) } returns kotlinx.coroutines.flow.flowOf(emptyList<RealmNews>())

        val flow = repository.getCommunityNews("testUser")
        val result = flow.toList()

        assertNotNull(result)
        io.mockk.verify { dispatcherProvider.default }
    }

    @Test
    fun `getNewsForUpload filters guest users and correctly serializes payloads`() = testScope.runTest {
        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any()) } answers { firstArg<CharSequence?>().isNullOrEmpty() }

        val mockRealm = mockk<io.realm.Realm>(relaxed = true)
        val mockRealmQuery = mockk<io.realm.RealmQuery<RealmNews>>(relaxed = true)

        val realGson = Gson()
        val repoWithRealGson = spyk(VoicesRepositoryImpl(
            databaseService,
            mainDispatcherRule.testDispatcher,
            dispatcherProvider,
            realGson,
            sharedPrefManager,
            dagger.Lazy { userRepository }
        ), recordPrivateCalls = true)

        coEvery { databaseService.withRealmAsync<Any>(any()) } answers {
            val block = firstArg<(io.realm.Realm) -> Any>()
            block(mockRealm)
        }

        coEvery { repoWithRealGson["queryList"](RealmNews::class.java, any<Boolean>(), any<Function1<*, *>>()) } answers {
            val block = thirdArg<io.realm.RealmQuery<RealmNews>.() -> Unit>()

            val guestNews = RealmNews().apply {
                id = "guest_news_id"
                userId = "guest_123"
            }
            val validNews = RealmNews().apply {
                id = "valid_news_id"
                _id = "valid_news_id"
                userId = "user_123"
                message = "Hello World"
                user = "{}"
                conversations = "[]"
            }

            listOf(guestNews, validNews)
        }

        val result = repoWithRealGson.getNewsForUpload()

        assertEquals(1, result.size)
        assertEquals("valid_news_id", result[0].id)
        assertEquals("Hello World", result[0].message)
        assertEquals("Hello World", result[0].newsJson.get("message").asString)
        assertNotNull(result[0].newsJson.get("user"))
    }

    @Test
    fun getDiscussionsByTeamIdFlow_uses_dispatcherProvider_default() = testScope.runTest {
        coEvery { repository["queryListFlow"](RealmNews::class.java, any<Function1<*, *>>()) } returns kotlinx.coroutines.flow.flowOf(emptyList<RealmNews>())

        val flow = repository.getDiscussionsByTeamIdFlow("testTeam")
        val result = flow.toList()

        assertNotNull(result)
        io.mockk.verify { dispatcherProvider.default }
    }


    @Test
    fun `getCommunityVisibleNews filters correctly based on viewableBy and viewIn`() = testScope.runTest {
        val mockRealm = mockk<io.realm.Realm>(relaxed = true)
        val mockRealmQuery = mockk<io.realm.RealmQuery<RealmNews>>(relaxed = true)

        val realGson = Gson()
        val repoWithRealGson = spyk(VoicesRepositoryImpl(
            databaseService,
            mainDispatcherRule.testDispatcher,
            dispatcherProvider,
            realGson,
            sharedPrefManager,
            dagger.Lazy { userRepository }
        ), recordPrivateCalls = true)

        coEvery { databaseService.withRealmAsync<Any>(any()) } answers {
            val block = firstArg<(io.realm.Realm) -> Any>()
            block(mockRealm)
        }

        val news1 = RealmNews().apply {
            viewableBy = "community"
            viewIn = null
        }
        val news2 = RealmNews().apply {
            viewableBy = "other"
            viewIn = "[{\"_id\":\"user1\"}]"
        }
        val news3 = RealmNews().apply {
            viewableBy = "other"
            viewIn = "[{\"_id\":\"user2\"}]"
        }

        every { mockRealm.where(RealmNews::class.java) } returns mockRealmQuery
        every { mockRealmQuery.isEmpty("replyTo") } returns mockRealmQuery
        every { mockRealmQuery.equalTo("docType", "message", io.realm.Case.INSENSITIVE) } returns mockRealmQuery
        every { mockRealmQuery.sort("time", io.realm.Sort.DESCENDING) } returns mockRealmQuery

        val realmResults = mockk<io.realm.RealmResults<RealmNews>>()
        every { mockRealmQuery.findAll() } returns realmResults

        // Return a mock list from copyFromRealm
        every { mockRealm.copyFromRealm(realmResults as Iterable<RealmNews>) } returns listOf(news1, news2, news3)

        val result = repoWithRealGson.getCommunityVisibleNews("user1")

        org.junit.Assert.assertEquals(2, result.size)
        org.junit.Assert.assertEquals("community", result[0].viewableBy)
        org.junit.Assert.assertEquals("[{\"_id\":\"user1\"}]", result[1].viewIn)
    }

    @Test
    fun `getNewsByTeamId filters correctly based on viewableBy and viewIn`() = testScope.runTest {
        val mockRealm = mockk<io.realm.Realm>(relaxed = true)
        val mockRealmQuery = mockk<io.realm.RealmQuery<RealmNews>>(relaxed = true)

        val realGson = Gson()
        val repoWithRealGson = spyk(VoicesRepositoryImpl(
            databaseService,
            mainDispatcherRule.testDispatcher,
            dispatcherProvider,
            realGson,
            sharedPrefManager,
            dagger.Lazy { userRepository }
        ), recordPrivateCalls = true)

        coEvery { databaseService.withRealmAsync<Any>(any()) } answers {
            val block = firstArg<(io.realm.Realm) -> Any>()
            block(mockRealm)
        }

        val news1 = RealmNews().apply {
            viewableBy = "teams"
            viewableId = "team1"
        }
        val news2 = RealmNews().apply {
            viewableBy = "other"
            viewIn = "[{\"_id\":\"team1\"}]"
        }
        val news3 = RealmNews().apply {
            viewableBy = "other"
            viewIn = "[{\"_id\":\"team2\"}]"
        }

        every { mockRealm.where(RealmNews::class.java) } returns mockRealmQuery
        every { mockRealmQuery.isEmpty("replyTo") } returns mockRealmQuery
        every { mockRealmQuery.beginGroup() } returns mockRealmQuery
        every { mockRealmQuery.equalTo("viewableBy", "teams", io.realm.Case.INSENSITIVE) } returns mockRealmQuery
        every { mockRealmQuery.equalTo("viewableId", "team1", io.realm.Case.INSENSITIVE) } returns mockRealmQuery
        every { mockRealmQuery.or() } returns mockRealmQuery
        every { mockRealmQuery.contains("viewIn", "\"_id\":\"team1\"", io.realm.Case.INSENSITIVE) } returns mockRealmQuery
        every { mockRealmQuery.endGroup() } returns mockRealmQuery
        every { mockRealmQuery.sort("time", io.realm.Sort.DESCENDING) } returns mockRealmQuery

        val realmResults = mockk<io.realm.RealmResults<RealmNews>>()
        every { mockRealmQuery.findAll() } returns realmResults
        every { mockRealm.copyFromRealm(realmResults as Iterable<RealmNews>) } returns listOf(news1, news2)

        val result = repoWithRealGson.getNewsByTeamId("team1")

        org.junit.Assert.assertEquals(2, result.size)
        org.junit.Assert.assertEquals("teams", result[0].viewableBy)
        org.junit.Assert.assertEquals("[{\"_id\":\"team1\"}]", result[1].viewIn)
    }

    @Test
    fun `getFilteredNews executes correct Realm query`() = testScope.runTest {
        val mockRealm = mockk<io.realm.Realm>(relaxed = true)
        val mockRealmQuery = mockk<io.realm.RealmQuery<RealmNews>>(relaxed = true)

        coEvery { databaseService.withRealmAsync<Any>(any()) } answers {
            val block = firstArg<(io.realm.Realm) -> Any>()
            block(mockRealm)
        }

        every { mockRealm.where(RealmNews::class.java) } returns mockRealmQuery
        every { mockRealmQuery.isEmpty("replyTo") } returns mockRealmQuery
        every { mockRealmQuery.beginGroup() } returns mockRealmQuery
        every { mockRealmQuery.equalTo("viewableBy", "teams", io.realm.Case.INSENSITIVE) } returns mockRealmQuery
        every { mockRealmQuery.equalTo("viewableId", "team1", io.realm.Case.INSENSITIVE) } returns mockRealmQuery
        every { mockRealmQuery.endGroup() } returns mockRealmQuery
        every { mockRealmQuery.or() } returns mockRealmQuery
        every { mockRealmQuery.contains("viewIn", "\"_id\":\"team1\"", io.realm.Case.INSENSITIVE) } returns mockRealmQuery
        every { mockRealmQuery.sort("time", io.realm.Sort.DESCENDING) } returns mockRealmQuery

        val realmResults = mockk<io.realm.RealmResults<RealmNews>>()
        every { mockRealmQuery.findAll() } returns realmResults

        val news1 = RealmNews()
        every { mockRealm.copyFromRealm(realmResults as Iterable<RealmNews>) } returns listOf(news1)

        val result = repository.getFilteredNews("team1")

        org.junit.Assert.assertEquals(1, result.size)
        io.mockk.verify(exactly = 1) { mockRealmQuery.beginGroup() }
        io.mockk.verify(exactly = 1) { mockRealmQuery.equalTo("viewableBy", "teams", io.realm.Case.INSENSITIVE) }
        io.mockk.verify(exactly = 1) { mockRealmQuery.equalTo("viewableId", "team1", io.realm.Case.INSENSITIVE) }
        io.mockk.verify(exactly = 1) { mockRealmQuery.endGroup() }
        io.mockk.verify(exactly = 1) { mockRealmQuery.or() }
        io.mockk.verify(exactly = 1) { mockRealmQuery.contains("viewIn", "\"_id\":\"team1\"", io.realm.Case.INSENSITIVE) }
    }

    @Test
    fun `deleteNews recursively deletes replies`() = testScope.runTest {
        val mockRealm = mockk<io.realm.Realm>(relaxed = true)

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val block = firstArg<(io.realm.Realm) -> Unit>()
            block(mockRealm)
        }

        val mockQueryLevel1 = mockk<io.realm.RealmQuery<RealmNews>>(relaxed = true)
        val mockQueryLevel2 = mockk<io.realm.RealmQuery<RealmNews>>(relaxed = true)
        val mockQueryTarget = mockk<io.realm.RealmQuery<RealmNews>>(relaxed = true)

        val realmResultsTarget = mockk<io.realm.RealmResults<RealmNews>>(relaxed = true)
        val realmResultsLevel1 = mockk<io.realm.RealmResults<RealmNews>>(relaxed = true)
        val realmResultsLevel2 = mockk<io.realm.RealmResults<RealmNews>>(relaxed = true)

        val reply1 = mockk<RealmNews>(relaxed = true)
        every { reply1.id } returns "reply1_id"
        val reply2 = mockk<RealmNews>(relaxed = true)
        every { reply2.id } returns "reply2_id"

        every { mockRealm.where(RealmNews::class.java) } returns mockQueryTarget
        every { mockQueryTarget.equalTo("replyTo", "newsId") } returns mockQueryLevel1
        every { mockQueryLevel1.findAll() } returns realmResultsLevel1
        every { realmResultsLevel1.iterator() } returns mutableListOf(reply1).iterator()

        every { mockQueryTarget.equalTo("replyTo", "reply1_id") } returns mockQueryLevel2
        every { mockQueryLevel2.findAll() } returns realmResultsLevel2
        every { realmResultsLevel2.iterator() } returns mutableListOf(reply2).iterator()

        every { mockQueryTarget.equalTo("replyTo", "reply2_id") } returns mockk(relaxed = true) {
            every { findAll() } returns mockk(relaxed = true) {
                every { iterator() } returns mutableListOf<RealmNews>().iterator()
            }
        }

        every { mockQueryTarget.equalTo("id", "newsId") } returns mockQueryTarget
        every { mockQueryTarget.findAll() } returns realmResultsTarget

        repository.deleteNews("newsId")

        io.mockk.verify(exactly = 1) { reply2.deleteFromRealm() }
        io.mockk.verify(exactly = 1) { reply1.deleteFromRealm() }
        io.mockk.verify(exactly = 1) { realmResultsTarget.deleteAllFromRealm() }
    }

    @Test
    fun `addLabel modifies realm object correctly`() = testScope.runTest {
        val mockRealm = mockk<io.realm.Realm>(relaxed = true)
        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val block = firstArg<(io.realm.Realm) -> Unit>()
            block(mockRealm)
        }

        val mockQuery = mockk<io.realm.RealmQuery<RealmNews>>(relaxed = true)
        val mockNews = mockk<RealmNews>(relaxed = true)
        val mockLabels = mockk<io.realm.RealmList<String>>(relaxed = true)

        every { mockRealm.where(RealmNews::class.java) } returns mockQuery
        every { mockQuery.equalTo("id", "newsId") } returns mockQuery
        every { mockQuery.findFirst() } returns mockNews
        every { mockNews.labels } returns mockLabels

        repository.addLabel("newsId", "testLabel")

        io.mockk.verify(exactly = 1) { mockLabels.add("testLabel") }
    }

    @Test
    fun `removeLabel modifies realm object correctly`() = testScope.runTest {
        val mockRealm = mockk<io.realm.Realm>(relaxed = true)
        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val block = firstArg<(io.realm.Realm) -> Unit>()
            block(mockRealm)
        }

        val mockQuery = mockk<io.realm.RealmQuery<RealmNews>>(relaxed = true)
        val mockNews = mockk<RealmNews>(relaxed = true)
        val mockLabels = mockk<io.realm.RealmList<String>>(relaxed = true)

        every { mockRealm.where(RealmNews::class.java) } returns mockQuery
        every { mockQuery.equalTo("id", "newsId") } returns mockQuery
        every { mockQuery.findFirst() } returns mockNews
        every { mockNews.labels } returns mockLabels

        repository.removeLabel("newsId", "testLabel")

        io.mockk.verify(exactly = 1) { mockLabels.remove("testLabel") }
    }

    @Test
    fun `getUserById delegates to userRepository`() = testScope.runTest {
        val testUserId = "test_user_123"
        val mockUser = mockk<RealmUser>()

        coEvery { userRepository.getUserById(testUserId) } returns mockUser

        val user = repository.getUserById(testUserId)
        assertEquals(mockUser, user)
    }
}
