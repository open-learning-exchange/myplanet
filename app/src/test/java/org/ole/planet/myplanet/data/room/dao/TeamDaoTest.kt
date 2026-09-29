package org.ole.planet.myplanet.data.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun `getNonArchivedReportsByTeamId orders by createdDate descending`() = runBlocking {
        teamDao.upsertAll(
            listOf(
                report("r1", createdDate = 100L),
                report("r3", createdDate = 300L),
                report("r2", createdDate = 200L),
            )
        )

        val result = teamDao.getNonArchivedReportsByTeamId("team1")

        assertEquals(listOf("r3", "r2", "r1"), result.map { it._id })
    }

    @Test
    fun `getNonArchivedReportsByTeamId excludes archived reports`() = runBlocking {
        teamDao.upsertAll(
            listOf(
                report("kept", createdDate = 100L),
                report("archived", createdDate = 300L, status = "archived"),
            )
        )

        val result = teamDao.getNonArchivedReportsByTeamId("team1")

        assertEquals(listOf("kept"), result.map { it._id })
    }

    @Test
    fun `getNonArchivedReportsByTeamId keeps reports with a null status`() = runBlocking {
        teamDao.upsertAll(
            listOf(
                report("nullStatus", createdDate = 100L, status = null),
                report("activeStatus", createdDate = 200L, status = "active"),
            )
        )

        val result = teamDao.getNonArchivedReportsByTeamId("team1")

        assertEquals(listOf("activeStatus", "nullStatus"), result.map { it._id })
    }

    @Test
    fun `getNonArchivedReportsByTeamId excludes other teams and other docTypes`() = runBlocking {
        teamDao.upsertAll(
            listOf(
                report("mine", createdDate = 100L),
                report("otherTeam", teamId = "team2", createdDate = 200L),
                report("transaction", createdDate = 300L, docType = "transaction"),
            )
        )

        val result = teamDao.getNonArchivedReportsByTeamId("team1")

        assertEquals(listOf("mine"), result.map { it._id })
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
}
