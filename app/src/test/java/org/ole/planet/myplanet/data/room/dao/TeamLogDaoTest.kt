package org.ole.planet.myplanet.data.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.TeamLog

@RunWith(AndroidJUnit4::class)
class TeamLogDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var teamLogDao: TeamLogDao

    @Before
    fun initDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        teamLogDao = database.teamLogDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun getRecentTeamVisits_returnsAllRowsAndFiltersByCutoffAcrossLargeChunkedList() = runBlocking {
        val cutoff = 1000L
        val logs = listOf(
            TeamLog().apply { id = "log_1"; type = "teamVisit"; teamId = "team_1"; time = 2000L },
            TeamLog().apply { id = "log_900"; type = "teamVisit"; teamId = "team_900"; time = 2000L },
            TeamLog().apply { id = "log_901"; type = "teamVisit"; teamId = "team_901"; time = 2000L },
            TeamLog().apply { id = "log_1200"; type = "teamVisit"; teamId = "team_1200"; time = 2000L },
            TeamLog().apply { id = "log_1201"; type = "teamVisit"; teamId = "team_1201"; time = 1000L },
            TeamLog().apply { id = "log_1300"; type = "teamVisit"; teamId = "team_1300"; time = 500L },
            TeamLog().apply { id = "log_non_visit"; type = "otherType"; teamId = "team_1"; time = 2000L },
            TeamLog().apply { id = "log_unrequested"; type = "teamVisit"; teamId = "team_1301"; time = 2000L }
        )

        teamLogDao.upsertAll(logs)

        val teamIds = (1..1300).map { "team_$it" }
        val results = teamLogDao.getRecentTeamVisits(cutoff, teamIds)

        assertEquals(4, results.size)
        assertEquals(setOf("log_1", "log_900", "log_901", "log_1200"), results.map { it.id }.toSet())
        assertTrue(results.all { (it.time ?: 0L) > cutoff })
    }

    @Test
    fun getTeamVisitsForUsers_returnsAllRowsAcrossLargeChunkedList() = runBlocking {
        val targetTeamId = "team_main"
        val logs = listOf(
            TeamLog().apply { id = "log_user_1"; type = "teamVisit"; teamId = targetTeamId; user = "user_1"; time = 1500L },
            TeamLog().apply { id = "log_user_900"; type = "teamVisit"; teamId = targetTeamId; user = "user_900"; time = 1500L },
            TeamLog().apply { id = "log_user_901"; type = "teamVisit"; teamId = targetTeamId; user = "user_901"; time = 1500L },
            TeamLog().apply { id = "log_user_1200"; type = "teamVisit"; teamId = targetTeamId; user = "user_1200"; time = 1500L },
            TeamLog().apply { id = "log_user_1301"; type = "teamVisit"; teamId = targetTeamId; user = "user_1301"; time = 1500L },
            TeamLog().apply { id = "log_user_other_team"; type = "teamVisit"; teamId = "other_team"; user = "user_1"; time = 1500L }
        )

        teamLogDao.upsertAll(logs)

        val userNames = (1..1300).map { "user_$it" }
        val results = teamLogDao.getTeamVisitsForUsers(targetTeamId, userNames)

        assertEquals(4, results.size)
        assertEquals(setOf("log_user_1", "log_user_900", "log_user_901", "log_user_1200"), results.map { it.id }.toSet())
        assertTrue(results.all { it.teamId == targetTeamId })
    }

    @Test
    fun emptyInput_returnsEmptyList() = runBlocking {
        val recentVisits = teamLogDao.getRecentTeamVisits(1000L, emptyList())
        val userVisits = teamLogDao.getTeamVisitsForUsers("team_1", emptyList())

        assertTrue(recentVisits.isEmpty())
        assertTrue(userVisits.isEmpty())
    }
}
