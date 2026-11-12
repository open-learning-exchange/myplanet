package org.ole.planet.myplanet.data.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.MyTeam

@RunWith(AndroidJUnit4::class)
class TeamDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var teamDao: TeamDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        teamDao = database.teamDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun report(
        id: String,
        teamId: String = "team1",
        createdDate: Long = 0L,
        status: String? = null,
        docType: String = "report",
    ) = MyTeam().apply {
        _id = id
        this.teamId = teamId
        this.createdDate = createdDate
        this.status = status
        this.docType = docType
    }

    @Test
    fun `observeNonArchivedFinanceReportsByTeamId orders by createdDate descending`() = runBlocking {
        teamDao.upsertAll(
            listOf(
                report("r1", createdDate = 100L),
                report("r3", createdDate = 300L),
                report("r2", createdDate = 200L),
            )
        )

        val result = teamDao.observeNonArchivedFinanceReportsByTeamId("team1").first()

        assertEquals(listOf("r3", "r2", "r1"), result.map { it._id })
    }

    @Test
    fun `observeNonArchivedFinanceReportsByTeamId excludes archived reports`() = runBlocking {
        teamDao.upsertAll(
            listOf(
                report("kept", createdDate = 100L),
                report("archived", createdDate = 300L, status = "archived"),
            )
        )

        val result = teamDao.observeNonArchivedFinanceReportsByTeamId("team1").first()

        assertEquals(listOf("kept"), result.map { it._id })
    }

    // The query uses IFNULL(status, '') so that rows with a NULL status still match:
    // a bare `status != 'archived'` never matches NULL in SQL and would drop them.
    @Test
    fun `observeNonArchivedFinanceReportsByTeamId keeps reports with a null status`() = runBlocking {
        teamDao.upsertAll(
            listOf(
                report("nullStatus", createdDate = 100L, status = null),
                report("activeStatus", createdDate = 200L, status = "active"),
            )
        )

        val result = teamDao.observeNonArchivedFinanceReportsByTeamId("team1").first()

        assertEquals(listOf("activeStatus", "nullStatus"), result.map { it._id })
    }

    @Test
    fun `observeNonArchivedFinanceReportsByTeamId excludes other teams and other docTypes`() = runBlocking {
        teamDao.upsertAll(
            listOf(
                report("mine", createdDate = 100L),
                report("otherTeam", teamId = "team2", createdDate = 200L),
                report("transaction", createdDate = 300L, docType = "transaction"),
            )
        )

        val result = teamDao.observeNonArchivedFinanceReportsByTeamId("team1").first()

        assertEquals(listOf("mine"), result.map { it._id })
    }

    @Test
    fun `observeNonArchivedFinanceReportsByTeamId projects all 15 fields including updated and imageName`() = runBlocking {
        val fullReport = MyTeam().apply {
            _id = "r1"
            _rev = "rev1"
            teamId = "team1"
            docType = "report"
            status = "active"
            description = "test description"
            beginningBalance = 100.0
            sales = 200.0
            otherIncome = 50.0
            wages = 30.0
            otherExpenses = 20.0
            startDate = 1000L
            endDate = 2000L
            createdDate = 3000L
            updatedDate = 4000L
            updated = true
            imageName = "report.png"
        }
        teamDao.upsert(fullReport)

        val result = teamDao.observeNonArchivedFinanceReportsByTeamId("team1").first()

        assertEquals(1, result.size)
        val financeReport = result[0]
        assertEquals("r1", financeReport._id)
        assertEquals("rev1", financeReport._rev)
        assertEquals("active", financeReport.status)
        assertEquals("test description", financeReport.description)
        assertEquals(100.0, financeReport.beginningBalance, 0.0)
        assertEquals(200.0, financeReport.sales, 0.0)
        assertEquals(50.0, financeReport.otherIncome, 0.0)
        assertEquals(30.0, financeReport.wages, 0.0)
        assertEquals(20.0, financeReport.otherExpenses, 0.0)
        assertEquals(1000L, financeReport.startDate)
        assertEquals(2000L, financeReport.endDate)
        assertEquals(3000L, financeReport.createdDate)
        assertEquals(4000L, financeReport.updatedDate)
        assertEquals(true, financeReport.updated)
        assertEquals("report.png", financeReport.imageName)
    }

    @Test
    fun `updateReportFields changes 9 report columns sets isUpdated and leaves other columns intact`() = runBlocking {
        val initial = MyTeam().apply {
            _id = "report1"
            _rev = "rev-123"
            teamId = "team1"
            createdDate = 1000L
            status = "active"
            imageName = "chart.png"
            description = "old desc"
            beginningBalance = 10.0
            sales = 20.0
            otherIncome = 30.0
            wages = 40.0
            otherExpenses = 50.0
            startDate = 100L
            endDate = 200L
            updatedDate = 300L
            updated = false
        }
        teamDao.upsert(initial)

        val count = teamDao.updateReportFields(
            id = "report1",
            description = "new desc",
            beginningBalance = 100.0,
            sales = 200.0,
            otherIncome = 300.0,
            wages = 400.0,
            otherExpenses = 500.0,
            startDate = 1000L,
            endDate = 2000L,
            updatedDate = 3000L
        )

        assertEquals(1, count)

        val updatedEntity = teamDao.getById("report1")!!
        assertEquals("new desc", updatedEntity.description)
        assertEquals(100.0, updatedEntity.beginningBalance, 0.0)
        assertEquals(200.0, updatedEntity.sales, 0.0)
        assertEquals(300.0, updatedEntity.otherIncome, 0.0)
        assertEquals(400.0, updatedEntity.wages, 0.0)
        assertEquals(500.0, updatedEntity.otherExpenses, 0.0)
        assertEquals(1000L, updatedEntity.startDate)
        assertEquals(2000L, updatedEntity.endDate)
        assertEquals(3000L, updatedEntity.updatedDate)
        assertEquals(true, updatedEntity.updated)

        assertEquals("rev-123", updatedEntity._rev)
        assertEquals("team1", updatedEntity.teamId)
        assertEquals(1000L, updatedEntity.createdDate)
        assertEquals("active", updatedEntity.status)
        assertEquals("chart.png", updatedEntity.imageName)
    }

    @Test
    fun `updateReportFields returns 0 and inserts nothing for unknown id`() = runBlocking {
        val count = teamDao.updateReportFields(
            id = "unknown_report",
            description = "desc",
            beginningBalance = 10.0,
            sales = 20.0,
            otherIncome = 30.0,
            wages = 40.0,
            otherExpenses = 50.0,
            startDate = 100L,
            endDate = 200L,
            updatedDate = 300L
        )

        assertEquals(0, count)
        assertEquals(null, teamDao.getById("unknown_report"))
    }

    @Test
    fun `archiveById hides row from observeNonArchivedFinanceReportsByTeamId and sets isUpdated`() = runBlocking {
        val initial = report("r1", teamId = "team1", createdDate = 100L).apply {
            updated = false
        }
        teamDao.upsert(initial)

        val updatedRows = teamDao.archiveById("r1")
        assertEquals(1, updatedRows)

        val reports = teamDao.observeNonArchivedFinanceReportsByTeamId("team1").first()
        assertEquals(0, reports.size)

        val updatedTeams = teamDao.getUpdatedTeams()
        assertEquals(1, updatedTeams.size)
        assertEquals("r1", updatedTeams[0]._id)
        assertEquals("archived", updatedTeams[0].status)
    }

    @Test
    fun `setImageNameById changes only imageName and isUpdated leaving other columns intact`() = runBlocking {
        val initial = report("r1", teamId = "team1", createdDate = 100L).apply {
            description = "original desc"
            sales = 500.0
            updated = false
            imageName = "old_logo.png"
        }
        teamDao.upsert(initial)

        val updatedRows = teamDao.setImageNameById("r1", "new_logo.png")
        assertEquals(1, updatedRows)

        val updatedEntity = teamDao.getById("r1")!!
        assertEquals("new_logo.png", updatedEntity.imageName)
        assertEquals(true, updatedEntity.updated)
        assertEquals("original desc", updatedEntity.description)
        assertEquals(500.0, updatedEntity.sales, 0.0)
        assertEquals("team1", updatedEntity.teamId)
        assertEquals(100L, updatedEntity.createdDate)
    }

    @Test
    fun `getNonArchivedReportCsvProjectionsByTeamId projects fields and orders descending`() = runBlocking {
        val r1 = report("r1", createdDate = 100L).apply {
            startDate = 10L
            endDate = 20L
            updatedDate = 30L
            beginningBalance = 100.0
            sales = 50.0
            otherIncome = 20.0
            wages = 10.0
            otherExpenses = 15.0
        }
        val r2 = report("r2", createdDate = 200L).apply {
            startDate = 40L
            endDate = 50L
            updatedDate = 60L
            beginningBalance = 200.0
            sales = 80.0
            otherIncome = 30.0
            wages = 20.0
            otherExpenses = 25.0
        }
        val archived = report("archived", createdDate = 300L, status = "archived")
        val otherTeam = report("otherTeam", teamId = "team2", createdDate = 400L)

        teamDao.upsertAll(listOf(r1, r2, archived, otherTeam))

        val projections = teamDao.getNonArchivedReportCsvProjectionsByTeamId("team1")

        assertEquals(2, projections.size)
        assertEquals(200L, projections[0].createdDate)
        assertEquals(40L, projections[0].startDate)
        assertEquals(50L, projections[0].endDate)
        assertEquals(60L, projections[0].updatedDate)
        assertEquals(200.0, projections[0].beginningBalance, 0.0)
        assertEquals(80.0, projections[0].sales, 0.0)
        assertEquals(30.0, projections[0].otherIncome, 0.0)
        assertEquals(20.0, projections[0].wages, 0.0)
        assertEquals(25.0, projections[0].otherExpenses, 0.0)

        assertEquals(100L, projections[1].createdDate)
        assertEquals(10L, projections[1].startDate)
        assertEquals(20L, projections[1].endDate)
        assertEquals(30L, projections[1].updatedDate)
        assertEquals(100.0, projections[1].beginningBalance, 0.0)
        assertEquals(50.0, projections[1].sales, 0.0)
        assertEquals(20.0, projections[1].otherIncome, 0.0)
        assertEquals(10.0, projections[1].wages, 0.0)
        assertEquals(15.0, projections[1].otherExpenses, 0.0)
    }

    @Test
    fun observeMemberTeams_returnsOnlyRootTeamsWithActiveMembershipOfUser() = runBlocking {
        val rootNull = MyTeam().apply { _id = "root_null"; teamId = null; docType = "team" }
        val rootEmpty = MyTeam().apply { _id = "root_empty"; teamId = ""; docType = "team" }
        val rootSpace = MyTeam().apply { _id = "root_space"; teamId = " "; docType = "team" }
        val rootFormFeed = MyTeam().apply { _id = "root_ff"; teamId = "\u000C"; docType = "team" }
        val notJoined = MyTeam().apply { _id = "not_joined"; teamId = null; docType = "team" }
        val otherUsersTeam = MyTeam().apply { _id = "other_team"; teamId = null; docType = "team" }
        val pendingTeam = MyTeam().apply { _id = "pending_team"; teamId = null; docType = "team" }
        val childRow = MyTeam().apply { _id = "child1"; teamId = "root_null"; docType = "report" }
        val reportForUser = MyTeam().apply { _id = "report1"; teamId = "not_joined"; userId = "user1"; docType = "report" }

        fun membership(id: String, user: String, team: String, pending: Boolean = false) = MyTeam().apply {
            _id = id; userId = user; teamId = team; docType = "membership"; isDeletePending = pending
        }

        teamDao.upsertAll(
            listOf(
                rootNull, rootEmpty, rootSpace, rootFormFeed, notJoined, otherUsersTeam, pendingTeam, childRow, reportForUser,
                membership("m1", "user1", "root_null"),
                membership("m2", "user1", "root_empty"),
                membership("m3", "user1", "root_space"),
                membership("m4", "user1", "root_ff"),
                membership("m5", "user1", "child1"),
                membership("m6", "user1", "pending_team", pending = true),
                membership("m7", "user2", "other_team")
            )
        )

        val resultIds = teamDao.observeMemberTeams("user1").first().map { it._id }.toSet()

        assertEquals(setOf("root_null", "root_empty", "root_space", "root_ff"), resultIds)
    }

    @Test
    fun getRootTeamsByTypeAndIds_handlesLargeInputAndDeduplicates() = runBlocking {
        val team1 = MyTeam().apply {
            _id = "team_0"
            type = "community"
            status = "active"
            teamId = ""
        }
        val team2 = MyTeam().apply {
            _id = "team_1000"
            type = "community"
            status = "active"
            teamId = ""
        }
        teamDao.upsertAll(listOf(team1, team2))

        val queryIds = (0 until 1200).map { "team_$it" }.toSet()
        val result = teamDao.getRootTeamsByTypeAndIds("community", queryIds)

        assertEquals(2, result.size)
        assertTrue(result.any { it._id == "team_0" })
        assertTrue(result.any { it._id == "team_1000" })
    }

    @Test
    fun getByIds_with1200IdsAndDuplicates_returnsAllWithoutDuplicates() = runBlocking {
        val teams = (1..1200).map { i ->
            MyTeam().apply {
                _id = "team_$i"
                name = "Team $i"
            }
        }
        teamDao.upsertAll(teams)

        val queryIds = (1..1200).map { i -> "team_$i" } + listOf("team_1", "team_500", "unknown_team")
        val results = teamDao.getByIds(queryIds)

        assertEquals(1200, results.size)
        assertEquals((1..1200).map { i -> "team_$i" }.toSet(), results.map { it._id }.toSet())
    }

    @Test
    fun deleteByIds_with1201IdsDuplicatesAndUnknowns_returnsDeletedCountAndLeavesUnrelatedRow() = runBlocking {
        val teamsToDelete = (1..1200).map { i ->
            MyTeam().apply {
                _id = "team_$i"
            }
        }
        val survivor = MyTeam().apply {
            _id = "survivor_team"
        }
        teamDao.upsertAll(teamsToDelete + survivor)

        val queryIds = (1..1200).map { i -> "team_$i" } + listOf("team_1", "team_900", "unknown_team_1", "unknown_team_2")
        val deletedCount = teamDao.deleteByIds(queryIds)

        assertEquals(1200, deletedCount)
        assertEquals(null, teamDao.getById("team_1"))
        assertEquals(null, teamDao.getById("team_1200"))
        val fetchedSurvivor = teamDao.getById("survivor_team")
        assertEquals("survivor_team", fetchedSurvivor?._id)
    }

    @Test
    fun emptyList_getByIdsReturnsEmpty_deleteByIdsReturnsZero() = runBlocking {
        teamDao.upsert(MyTeam().apply { _id = "team_1" })

        val getResult = teamDao.getByIds(emptyList())
        val deleteResult = teamDao.deleteByIds(emptyList())

        assertTrue(getResult.isEmpty())
        assertEquals(0, deleteResult)
        assertEquals("team_1", teamDao.getById("team_1")?._id)
    }
}
