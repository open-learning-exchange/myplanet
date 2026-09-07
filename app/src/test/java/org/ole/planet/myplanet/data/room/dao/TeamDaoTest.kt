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
}
