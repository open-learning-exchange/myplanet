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
        val logs = mutableListOf<TeamLog>()

        // Insert 1,200 team-visit logs with time > cutoff
        for (i in 1..1200) {
            logs.add(TeamLog().apply {
                id = "log_$i"
                type = "teamVisit"
                teamId = "team_$i"
                time = 2000L
            })
        }

        // Insert 100 older team-visit logs with time <= cutoff
        for (i in 1201..1300) {
            logs.add(TeamLog().apply {
                id = "log_$i"
                type = "teamVisit"
                teamId = "team_$i"
                time = 500L
            })
        }

        teamLogDao.upsertAll(logs)

        val teamIds = (1..1300).map { "team_$it" }
        val results = teamLogDao.getRecentTeamVisits(cutoff, teamIds)

        assertEquals(1200, results.size)
        assertTrue(results.all { (it.time ?: 0L) > cutoff })
    }

    @Test
    fun getTeamVisitsForUsers_returnsAllRowsAcrossLargeChunkedList() = runBlocking {
        val targetTeamId = "team_main"
        val logs = mutableListOf<TeamLog>()

        for (i in 1..1200) {
            logs.add(TeamLog().apply {
                id = "log_user_$i"
                type = "teamVisit"
                teamId = targetTeamId
                user = "user_$i"
                time = 1500L
            })
        }

        teamLogDao.upsertAll(logs)

        val userNames = (1..1200).map { "user_$it" }
        val results = teamLogDao.getTeamVisitsForUsers(targetTeamId, userNames)

        assertEquals(1200, results.size)
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
