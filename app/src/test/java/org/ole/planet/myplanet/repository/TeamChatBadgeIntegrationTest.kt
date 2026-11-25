package org.ole.planet.myplanet.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.TestTimeProvider
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TeamChatBadgeIntegrationTest {

    private lateinit var database: AppDatabase
    private lateinit var newsDao: org.ole.planet.myplanet.data.room.dao.NewsDao
    private lateinit var teamNotificationDao: org.ole.planet.myplanet.data.room.dao.TeamNotificationDao
    private lateinit var voicesRepository: VoicesRepositoryImpl
    private lateinit var notificationsRepository: NotificationsRepositoryImpl

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        newsDao = database.newsDao()
        teamNotificationDao = database.teamNotificationDao()

        val plainGson = Gson()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val sharedPrefManager = SharedPrefManager(context, Gson())
        val testDispatcher = UnconfinedTestDispatcher()
        voicesRepository = VoicesRepositoryImpl(
            TestDispatcherProvider(testDispatcher),
            Gson(),
            plainGson,
            sharedPrefManager,
            newsDao,
            database.newsLogDao(),
            database.removedLogDao(),
        )

        val userRepository = dagger.Lazy { mockk<UserRepository>(relaxed = true) }
        val teamsRepository = dagger.Lazy { mockk<TeamsNotificationsRepository>(relaxed = true) }
        notificationsRepository = NotificationsRepositoryImpl(
            userRepository,
            teamsRepository,
            TestTimeProvider(),
            teamNotificationDao,
            mockk(relaxed = true),
            voicesRepository,
            mockk(relaxed = true),
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun topLevel(viewIn: String? = null, viewableBy: String? = null, viewableId: String? = null): News =
        News().apply {
            id = java.util.UUID.randomUUID().toString()
            time = System.nanoTime()
            this.viewIn = viewIn
            this.viewableBy = viewableBy
            this.viewableId = viewableId
        }

    private fun replyTo(parent: News, viewIn: String? = null): News =
        News().apply {
            id = java.util.UUID.randomUUID().toString()
            time = System.nanoTime()
            replyTo = parent.id
            this.viewIn = viewIn
        }

    @Test
    fun `badge clears after the feed is opened, even with replies present`() = runBlocking {
        val teamId = "teamA"
        val local = topLevel(viewIn = "[{\"_id\":\"$teamId\",\"section\":\"teams\"}]")
        val reply = replyTo(local, viewIn = "[{\"_id\":\"$teamId\",\"section\":\"teams\"}]")
        newsDao.upsertAll(listOf(local, reply))

        val before = notificationsRepository.getTeamNotifications(listOf(teamId), "user1")
        assertFalse(before[teamId]?.hasChat == true)
        notificationsRepository.updateTeamNotification(teamId, voicesRepository.getFilteredNews(teamId).size)

        val after = notificationsRepository.getTeamNotifications(listOf(teamId), "user1")
        assertFalse(after[teamId]?.hasChat == true)
    }

    @Test
    fun `badge appears when a new top-level post arrives after the feed was opened`() = runBlocking {
        val teamId = "teamB"

        val first = topLevel(viewableBy = "teams", viewableId = teamId)
        newsDao.upsertAll(listOf(first))
        notificationsRepository.updateTeamNotification(teamId, voicesRepository.getFilteredNews(teamId).size)

        assertFalse(notificationsRepository.getTeamNotifications(listOf(teamId), "user1")[teamId]?.hasChat == true)

        newsDao.upsertAll(listOf(topLevel(viewIn = "[{\"_id\":\"$teamId\",\"section\":\"teams\"}]")))

        val result = notificationsRepository.getTeamNotifications(listOf(teamId), "user1")
        assertTrue(result[teamId]?.hasChat == true)
    }

    @Test
    fun `the viewer's own synced post does not light the badge but someone else's does`() = runBlocking {
        val teamId = "teamE"
        val viewer = "org.couchdb.user:dewie"
        newsDao.upsertAll(listOf(topLevel(viewableBy = "teams", viewableId = teamId)))
        notificationsRepository.updateTeamNotification(teamId, voicesRepository.countTopLevelByTeam(teamId, viewer).toInt())

        newsDao.upsertAll(listOf(topLevel(viewableBy = "teams", viewableId = teamId).apply { userId = viewer }))
        assertFalse(notificationsRepository.getTeamNotifications(listOf(teamId), viewer)[teamId]?.hasChat == true)

        newsDao.upsertAll(listOf(topLevel(viewableBy = "teams", viewableId = teamId).apply { userId = "org.couchdb.user:okuro" }))
        assertTrue(notificationsRepository.getTeamNotifications(listOf(teamId), viewer)[teamId]?.hasChat == true)
    }

    @Test
    fun `badge tracks the top-level feed, not replies`() = runBlocking {
        val teamId = "teamC"
        val root = topLevel(viewIn = "[{\"_id\":\"$teamId\",\"section\":\"teams\"}]")
        newsDao.upsertAll(listOf(root))
        notificationsRepository.updateTeamNotification(teamId, voicesRepository.getFilteredNews(teamId).size)

        newsDao.upsertAll(listOf(replyTo(root, viewIn = "[{\"_id\":\"$teamId\",\"section\":\"teams\"}]")))
        val result = notificationsRepository.getTeamNotifications(listOf(teamId), "user1")
        assertFalse(result[teamId]?.hasChat == true)
    }

    @Test
    fun `count used for chatCount equals the displayed top-level feed size`() = runBlocking {
        val teamId = "teamD"
        val a = topLevel(viewIn = "[{\"_id\":\"$teamId\",\"section\":\"teams\"}]")
        val b = topLevel(viewableBy = "teams", viewableId = teamId)
        val c = replyTo(a, viewIn = "[{\"_id\":\"$teamId\",\"section\":\"teams\"}]")
        newsDao.upsertAll(listOf(a, b, c))

        val displayedSize = voicesRepository.getFilteredNews(teamId).size
        val count = voicesRepository.countTopLevelByTeam(teamId)
        assertEquals(2L, count)
        assertEquals(displayedSize.toLong(), count)
    }
}
