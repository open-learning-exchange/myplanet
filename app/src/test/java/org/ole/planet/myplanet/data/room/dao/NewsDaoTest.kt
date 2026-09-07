package org.ole.planet.myplanet.data.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.News

@RunWith(AndroidJUnit4::class)
class NewsDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var newsDao: NewsDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        newsDao = database.newsDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun communityViewIn(): String =
        Gson().toJson(JsonArray().apply { add(JsonObject().apply {
            addProperty("section", "community")
            addProperty("_id", "planet@parent")
        }) })

    private fun teamViewIn(): String =
        Gson().toJson(JsonArray().apply { add(JsonObject().apply {
            addProperty("section", "teams")
            addProperty("_id", "team_123")
        }) })

    private fun teamIdPattern(teamId: String): String {
        val escaped = teamId
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%\"_id\":\"$escaped\"%"
    }

    @Test
    fun getTopLevelByTeam_filtersCorrectly() = runBlocking {
        val news1 = News().apply {
            id = UUID.randomUUID().toString()
            time = 100L
            viewableBy = "teams"
            viewableId = "team1"
        }

        val news2 = News().apply {
            id = UUID.randomUUID().toString()
            time = 200L
            viewableBy = "other"
            viewIn = "[{\"_id\":\"team1\"}]"
        }

        val news3 = News().apply {
            id = UUID.randomUUID().toString()
            time = 300L
            viewableBy = "teams"
            viewableId = "team2"
        }

        val news4 = News().apply {
            id = UUID.randomUUID().toString()
            time = 400L
            viewableBy = "other"
            viewIn = "[{\"_id\":\"team1Xsub\"}]"
        }

        val news5 = News().apply {
            id = UUID.randomUUID().toString()
            time = 500L
            viewableBy = "other"
            viewIn = "[{\"_id\":\"team1X\"}]"
        }

        newsDao.upsertAll(listOf(news1, news2, news3, news4, news5))

        val result1 = newsDao.getTopLevelByTeam("team1", teamIdPattern("team1"))
        assertEquals(2, result1.size)
        assertTrue(result1.any { it.id == news1.id })
        assertTrue(result1.any { it.id == news2.id })

        val resultSub = newsDao.getTopLevelByTeam("team1_sub", teamIdPattern("team1_sub"))
        assertEquals(0, resultSub.size) // Should not match team1Xsub

        val resultPerc = newsDao.getTopLevelByTeam("team1%", teamIdPattern("team1%"))
        assertEquals(0, resultPerc.size) // Should not match team1X
    }

    @Test
    fun getNewsAndRepliesIds_fetchesAllRecursiveReplies() = runBlocking {
        val root = News().apply { id = "root" }
        val reply1 = News().apply { id = "reply1"; replyTo = "root" }
        val reply2 = News().apply { id = "reply2"; replyTo = "reply1" }
        val reply3 = News().apply { id = "reply3"; replyTo = "reply2" }
        val leaf = News().apply { id = "leaf" }

        newsDao.upsertAll(listOf(root, reply1, reply2, reply3, leaf))

        val ids = newsDao.getNewsAndRepliesIds("root")
        assertEquals(4, ids.size)
        assertTrue(ids.contains("root"))
        assertTrue(ids.contains("reply1"))
        assertTrue(ids.contains("reply2"))
        assertTrue(ids.contains("reply3"))
    }

    @Test
    fun countTopLevelByTeam_matches_getTopLevelByTeam_size() = runBlocking {
        val localTop = News().apply {
            id = UUID.randomUUID().toString()
            time = 100L
            viewIn = "[{\"_id\":\"teamA\",\"section\":\"teams\"}]"
        }
        val syncedTop = News().apply {
            id = UUID.randomUUID().toString()
            time = 200L
            viewableBy = "teams"
            viewableId = "teamA"
        }
        val reply = News().apply {
            id = UUID.randomUUID().toString()
            time = 150L
            replyTo = localTop.id
            viewIn = "[{\"_id\":\"teamA\",\"section\":\"teams\"}]"
        }
        val otherTeam = News().apply {
            id = UUID.randomUUID().toString()
            time = 300L
            viewIn = "[{\"_id\":\"teamB\",\"section\":\"teams\"}]"
        }

        newsDao.upsertAll(listOf(localTop, syncedTop, reply, otherTeam))

        val pattern = teamIdPattern("teamA")
        val listSize = newsDao.getTopLevelByTeam("teamA", pattern).size
        val count = newsDao.countTopLevelByTeam("teamA", pattern)

        assertEquals(2, listSize)
        assertEquals(listSize.toLong(), count)
    }

    @Test
    fun countDistinctCommunityVoiceDates_returns_unique_day_count() = runBlocking {
        // 2024-12-27 00:00 UTC
        val day1 = News().apply {
            id = UUID.randomUUID().toString()
            time = 1735257600000L
            viewIn = communityViewIn()
        }
        // 2024-12-28 00:00 UTC
        val day2 = News().apply {
            id = UUID.randomUUID().toString()
            time = 1735344000000L
            viewIn = communityViewIn()
        }
        // Same UTC calendar day as day2 -> collapses into one distinct date.
        val day2SameDay = News().apply {
            id = UUID.randomUUID().toString()
            time = 1735344000000L + 3_600_000L // 2024-12-28 01:00 UTC
            viewIn = communityViewIn()
        }
        // Non-community post -> excluded even though it shares day1's timestamp.
        val teamPost = News().apply {
            id = UUID.randomUUID().toString()
            time = 1735257600000L
            viewIn = teamViewIn()
        }

        newsDao.upsertAll(listOf(day1, day2, day2SameDay, teamPost))

        val count = newsDao.countDistinctCommunityVoiceDates(1735257600000L, 1735430400000L)
        assertEquals(2, count)
    }

    @Test
    fun countDistinctCommunityVoiceDatesForUser_scopes_by_userId() = runBlocking {
        // 2024-12-27 00:00 UTC
        val user1Day1 = News().apply {
            id = UUID.randomUUID().toString()
            time = 1735257600000L
            userId = "user1"
            viewIn = communityViewIn()
        }
        // 2024-12-28 00:00 UTC
        val user1Day2 = News().apply {
            id = UUID.randomUUID().toString()
            time = 1735344000000L
            userId = "user1"
            viewIn = communityViewIn()
        }
        val user2Day1 = News().apply {
            id = UUID.randomUUID().toString()
            time = 1735257600000L
            userId = "user2"
            viewIn = communityViewIn()
        }

        newsDao.upsertAll(listOf(user1Day1, user1Day2, user2Day1))

        val user1Count = newsDao.countDistinctCommunityVoiceDatesForUser(
            1735257600000L, 1735430400000L, "user1"
        )
        assertEquals(2, user1Count)

        val user2Count = newsDao.countDistinctCommunityVoiceDatesForUser(
            1735257600000L, 1735430400000L, "user2"
        )
        assertEquals(1, user2Count)

        // All-community count covers both users (two distinct days across the set).
        val allCount = newsDao.countDistinctCommunityVoiceDates(1735257600000L, 1735430400000L)
        assertEquals(2, allCount)
    }
}
