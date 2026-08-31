package org.ole.planet.myplanet.data.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    private fun teamIdPattern(teamId: String): String {
        val escaped = teamId
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%\"_id\":\"$escaped\"%"
    }

    @Test
    fun getTopLevelByTeam_filtersCorrectly() = runBlocking {
        // Matches by viewableBy/viewableId
        val news1 = News().apply {
            id = UUID.randomUUID().toString()
            time = 100L
            viewableBy = "teams"
            viewableId = "team1"
        }

        // Matches by viewIn exact match
        val news2 = News().apply {
            id = UUID.randomUUID().toString()
            time = 200L
            viewableBy = "other"
            viewIn = "[{\"_id\":\"team1\"}]"
        }

        // Does not match, wrong team
        val news3 = News().apply {
            id = UUID.randomUUID().toString()
            time = 300L
            viewableBy = "teams"
            viewableId = "team2"
        }

        // Malicious match: team1_sub where _ acts as wildcard if unescaped
        val news4 = News().apply {
            id = UUID.randomUUID().toString()
            time = 400L
            viewableBy = "other"
            viewIn = "[{\"_id\":\"team1Xsub\"}]"
        }

        // Malicious match: team1% where % acts as wildcard if unescaped
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
        // Locally-created team message: only viewIn is set (News.createNews path), no viewableBy/viewableId.
        val localTop = News().apply {
            id = UUID.randomUUID().toString()
            time = 100L
            viewIn = "[{\"_id\":\"teamA\",\"section\":\"teams\"}]"
        }
        // Server-synced team message matched via viewableBy/viewableId.
        val syncedTop = News().apply {
            id = UUID.randomUUID().toString()
            time = 200L
            viewableBy = "teams"
            viewableId = "teamA"
        }
        // A reply to the local message — must NOT be counted (notification tracks top-level feed).
        val reply = News().apply {
            id = UUID.randomUUID().toString()
            time = 150L
            replyTo = localTop.id
            viewIn = "[{\"_id\":\"teamA\",\"section\":\"teams\"}]"
        }
        // A top-level message for a different team — must NOT be counted.
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
}
