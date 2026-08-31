package org.ole.planet.myplanet.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.di.PlainGson
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.TestTimeProvider

/**
 * Room-backed integration test for the team-chat badge.
 *
 * The badge is `hasChat = lastCount < chatCount`, where `lastCount` is the watermark written by
 * `TeamsVoicesViewModel.getFilteredNews` (the size of the displayed top-level feed) and
 * `chatCount` is the current count computed in `getTeamNotifications`. Both must measure the
 * same set of posts (top-level, visible to the team via either `viewableBy`/`viewableId` or the
 * `viewIn` path); otherwise the badge never clears (replies inflate `chatCount`) or never
 * appears (locally-created `viewIn`-only posts are invisible to `chatCount`).
 *
 * This wires a real in-memory Room DB through `VoicesRepositoryImpl.countTopLevelByTeam` and
 * `NotificationsRepositoryImpl` so the comparison is exercised end-to-end against real SQL.
 */
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
        val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()
        voicesRepository = VoicesRepositoryImpl(
            TestDispatcherProvider(testDispatcher),
            Gson(),
            plainGson,
            sharedPrefManager,
            newsDao,
            database.newsLogDao(),
        )

        val userRepository = dagger.Lazy { mockk<UserRepository>(relaxed = true) }
        val teamsRepository = dagger.Lazy { mockk<TeamsNotificationsRepository>(relaxed = true) }
        val teamTaskDao = mockk<org.ole.planet.myplanet.data.room.dao.TeamTaskDao>(relaxed = true)
        coEvery { teamTaskDao.getTasksForUserBetween(any(), any(), any()) } returns emptyList()
        notificationsRepository = NotificationsRepositoryImpl(
            userRepository,
            teamsRepository,
            TestTimeProvider(),
            teamNotificationDao,
            mockk(relaxed = true),
            teamTaskDao,
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
        // One top-level post visible via the viewIn path (locally-created) ...
        val local = topLevel(viewIn = "[{\"_id\":\"$teamId\",\"section\":\"teams\"}]")
        // ... plus a reply to it. The old chatCount counted the reply, so lastCount < chatCount
        // forever and the badge never cleared.
        val reply = replyTo(local, viewIn = "[{\"_id\":\"$teamId\",\"section\":\"teams\"}]")
        newsDao.upsertAll(listOf(local, reply))

        // Before the feed is opened there is no watermark yet → no badge.
        val before = notificationsRepository.getTeamNotifications(listOf(teamId), "user1")
        assertFalse(before[teamId]?.hasChat == true)

        // Opening the feed writes the watermark = number of top-level posts shown (1).
        notificationsRepository.updateTeamNotification(teamId, voicesRepository.countTopLevelByTeam(teamId).toInt())

        // No new posts since → chatCount equals the watermark → badge cleared.
        val after = notificationsRepository.getTeamNotifications(listOf(teamId), "user1")
        assertFalse(after[teamId]?.hasChat == true)
    }

    @Test
    fun `badge appears when a new top-level post arrives after the feed was opened`() = runBlocking {
        val teamId = "teamB"
        // Synced post visible via viewableBy/viewableId.
        val first = topLevel(viewableBy = "teams", viewableId = teamId)
        newsDao.upsertAll(listOf(first))
        notificationsRepository.updateTeamNotification(teamId, voicesRepository.countTopLevelByTeam(teamId).toInt())

        // Same count → no badge.
        assertFalse(notificationsRepository.getTeamNotifications(listOf(teamId), "user1")[teamId]?.hasChat == true)

        // A new top-level post arrives (here via the viewIn path, the locally-created case the
        // old chatCount missed entirely).
        newsDao.upsertAll(listOf(topLevel(viewIn = "[{\"_id\":\"$teamId\",\"section\":\"teams\"}]")))

        // chatCount (2) > lastCount (1) → badge appears.
        val result = notificationsRepository.getTeamNotifications(listOf(teamId), "user1")
        assertTrue(result[teamId]?.hasChat == true)
    }

    @Test
    fun `badge tracks the top-level feed, not replies`() = runBlocking {
        val teamId = "teamC"
        val root = topLevel(viewIn = "[{\"_id\":\"$teamId\",\"section\":\"teams\"}]")
        newsDao.upsertAll(listOf(root))
        notificationsRepository.updateTeamNotification(teamId, voicesRepository.countTopLevelByTeam(teamId).toInt())

        // Adding replies must not raise the badge: they are not part of the top-level feed.
        newsDao.upsertAll(listOf(replyTo(root, viewIn = "[{\"_id\":\"$teamId\",\"section\":\"teams\"}]")))
        val result = notificationsRepository.getTeamNotifications(listOf(teamId), "user1")
        assertFalse(result[teamId]?.hasChat == true)
    }

    @Test
    fun `count used for chatCount equals the displayed top-level feed size`() = runBlocking {
        val teamId = "teamD"
        val a = topLevel(viewIn = "[{\"_id\":\"$teamId\",\"section\":\"teams\"}]")
        val b = topLevel(viewableBy = "teams", viewableId = teamId)
        val c = replyTo(a, viewIn = "[{\"_id\":\"$teamId\",\"section\":\"teams\"}]") // reply, excluded
        newsDao.upsertAll(listOf(a, b, c))

        // The badge's chatCount comes from countTopLevelByTeam, which must equal the size of the
        // feed the user sees (getFilteredNews → getTopLevelByTeam): 2 top-level posts, reply excluded.
        val displayedSize = voicesRepository.getFilteredNews(teamId).size
        val count = voicesRepository.countTopLevelByTeam(teamId)
        assertEquals(2L, count)
        assertEquals(displayedSize.toLong(), count)
    }
}
