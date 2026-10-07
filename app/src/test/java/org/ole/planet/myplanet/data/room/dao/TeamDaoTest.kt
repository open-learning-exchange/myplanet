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
    fun `observeNonArchivedReportsByTeamId orders by createdDate descending`() = runBlocking {
        teamDao.upsertAll(
            listOf(
                report("r1", createdDate = 100L),
                report("r3", createdDate = 300L),
                report("r2", createdDate = 200L),
            )
        )

        val result = teamDao.observeNonArchivedReportsByTeamId("team1").first()

        assertEquals(listOf("r3", "r2", "r1"), result.map { it._id })
    }

    @Test
    fun `observeNonArchivedReportsByTeamId excludes archived reports`() = runBlocking {
        teamDao.upsertAll(
            listOf(
                report("kept", createdDate = 100L),
                report("archived", createdDate = 300L, status = "archived"),
            )
        )

        val result = teamDao.observeNonArchivedReportsByTeamId("team1").first()

        assertEquals(listOf("kept"), result.map { it._id })
    }

    // The query uses IFNULL(status, '') so that rows with a NULL status still match:
    // a bare `status != 'archived'` never matches NULL in SQL and would drop them.
    @Test
    fun `observeNonArchivedReportsByTeamId keeps reports with a null status`() = runBlocking {
        teamDao.upsertAll(
            listOf(
                report("nullStatus", createdDate = 100L, status = null),
                report("activeStatus", createdDate = 200L, status = "active"),
            )
        )

        val result = teamDao.observeNonArchivedReportsByTeamId("team1").first()

        assertEquals(listOf("activeStatus", "nullStatus"), result.map { it._id })
    }

    @Test
    fun `observeNonArchivedReportsByTeamId excludes other teams and other docTypes`() = runBlocking {
        teamDao.upsertAll(
            listOf(
                report("mine", createdDate = 100L),
                report("otherTeam", teamId = "team2", createdDate = 200L),
                report("transaction", createdDate = 300L, docType = "transaction"),
            )
        )

        val result = teamDao.observeNonArchivedReportsByTeamId("team1").first()

        assertEquals(listOf("mine"), result.map { it._id })
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
            beginningBalance = 10
            sales = 20
            otherIncome = 30
            wages = 40
            otherExpenses = 50
            startDate = 100L
            endDate = 200L
            updatedDate = 300L
            updated = false
        }
        teamDao.upsert(initial)

        val count = teamDao.updateReportFields(
            id = "report1",
            description = "new desc",
            beginningBalance = 100,
            sales = 200,
            otherIncome = 300,
            wages = 400,
            otherExpenses = 500,
            startDate = 1000L,
            endDate = 2000L,
            updatedDate = 3000L
        )

        assertEquals(1, count)

        val updatedEntity = teamDao.getById("report1")!!
        assertEquals("new desc", updatedEntity.description)
        assertEquals(100, updatedEntity.beginningBalance)
        assertEquals(200, updatedEntity.sales)
        assertEquals(300, updatedEntity.otherIncome)
        assertEquals(400, updatedEntity.wages)
        assertEquals(500, updatedEntity.otherExpenses)
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
            beginningBalance = 10,
            sales = 20,
            otherIncome = 30,
            wages = 40,
            otherExpenses = 50,
            startDate = 100L,
            endDate = 200L,
            updatedDate = 300L
        )

        assertEquals(0, count)
        assertEquals(null, teamDao.getById("unknown_report"))
    }

    @Test
    fun `archiveById hides row from observeNonArchivedReportsByTeamId and sets isUpdated`() = runBlocking {
        val initial = report("r1", teamId = "team1", createdDate = 100L).apply {
            updated = false
        }
        teamDao.upsert(initial)

        val updatedRows = teamDao.archiveById("r1")
        assertEquals(1, updatedRows)

        val reports = teamDao.observeNonArchivedReportsByTeamId("team1").first()
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
            sales = 500
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
        assertEquals(500, updatedEntity.sales)
        assertEquals("team1", updatedEntity.teamId)
        assertEquals(100L, updatedEntity.createdDate)
    }

    @Test
    fun `getNonArchivedReportCsvProjectionsByTeamId projects fields and orders descending`() = runBlocking {
        val r1 = report("r1", createdDate = 100L).apply {
            startDate = 10L
            endDate = 20L
            updatedDate = 30L
            beginningBalance = 100
            sales = 50
            otherIncome = 20
            wages = 10
            otherExpenses = 15
        }
        val r2 = report("r2", createdDate = 200L).apply {
            startDate = 40L
            endDate = 50L
            updatedDate = 60L
            beginningBalance = 200
            sales = 80
            otherIncome = 30
            wages = 20
            otherExpenses = 25
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
        assertEquals(200, projections[0].beginningBalance)
        assertEquals(80, projections[0].sales)
        assertEquals(30, projections[0].otherIncome)
        assertEquals(20, projections[0].wages)
        assertEquals(25, projections[0].otherExpenses)

        assertEquals(100L, projections[1].createdDate)
        assertEquals(10L, projections[1].startDate)
        assertEquals(20L, projections[1].endDate)
        assertEquals(30L, projections[1].updatedDate)
        assertEquals(100, projections[1].beginningBalance)
        assertEquals(50, projections[1].sales)
        assertEquals(20, projections[1].otherIncome)
        assertEquals(10, projections[1].wages)
        assertEquals(15, projections[1].otherExpenses)
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
}
