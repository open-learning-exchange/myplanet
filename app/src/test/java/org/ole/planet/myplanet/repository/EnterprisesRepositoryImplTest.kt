package org.ole.planet.myplanet.repository

import android.content.Context
import io.mockk.coEvery
import io.mockk.verify
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.TeamDao
import org.ole.planet.myplanet.model.FinanceReportParams
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.TimeProvider
import org.ole.planet.myplanet.utils.TimeUtils

@OptIn(ExperimentalCoroutinesApi::class)
class EnterprisesRepositoryImplTest {

    private val teamDao: TeamDao = mockk(relaxed = true)
    private val timeProvider: TimeProvider = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val dispatcherProvider: DispatcherProvider = TestDispatcherProvider(UnconfinedTestDispatcher())

    private val repository = EnterprisesRepositoryImpl(
        context, teamDao, timeProvider, dispatcherProvider
    )

    @After
    fun tearDown() {
        unmockkObject(FileUtils)
    }

    @Test
    fun `getReportsFlow delegates to observeNonArchivedReportsByTeamId`() = runTest {
        val teamId = "team123"
        val expectedReports = listOf(
            MyTeam().apply {
                _id = "report1"
                createdDate = 2000L
            },
            MyTeam().apply {
                _id = "report2"
                createdDate = 1000L
            }
        )

        every { teamDao.observeNonArchivedReportsByTeamId(teamId) } returns flowOf(expectedReports)

        val result = repository.getReportsFlow(teamId).first()

        assertEquals(expectedReports, result)
        verify(exactly = 1) { teamDao.observeNonArchivedReportsByTeamId(teamId) }
    }

    @Test
    fun `exportReportsAsCsv calculates correct profitLoss and endingBalance`() = runTest {
        val teamId = "team123"
        val report = MyTeam().apply {
            startDate = 1000L
            endDate = 2000L
            createdDate = 3000L
            updatedDate = 4000L
            beginningBalance = 100
            sales = 50
            otherIncome = 20
            wages = 10
            otherExpenses = 15
        }
        val archivedReport = MyTeam().apply {
            status = "archived"
            createdDate = 4000L
        }

        coEvery { teamDao.getByTeamIdAndDocType(teamId, "report") } returns listOf(report, archivedReport)

        val result = repository.exportReportsAsCsv(teamId, "Test Team")

        val expectedTotalIncome = 50 + 20 // 70
        val expectedTotalExpenses = 10 + 15 // 25
        val expectedProfitLoss = 70 - 25 // 45
        val expectedEndingBalance = 45 + 100 // 145

        val startDateFormatted = TimeUtils.formatDateForCsv(1000L)
        val endDateFormatted = TimeUtils.formatDateForCsv(2000L)
        val createdDateFormatted = TimeUtils.formatDateForCsv(3000L)
        val updatedDateFormatted = TimeUtils.formatDateForCsv(4000L)

        val expectedCsv = StringBuilder()
            .append("Test Team").append(" Financial Report Summary\n\n")
            .append("Start Date, End Date, Created Date, Updated Date, Beginning Balance, Sales, Other Income, Wages, Other Expenses, Profit/Loss, Ending Balance\n")
            .append(startDateFormatted).append(", ")
            .append(endDateFormatted).append(", ")
            .append(createdDateFormatted).append(", ")
            .append(updatedDateFormatted).append(", ")
            .append("100, ")
            .append("50, ")
            .append("20, ")
            .append("10, ")
            .append("15, ")
            .append("45, ")
            .append("145\n")
            .toString()

        assertEquals(expectedCsv, result)
    }

    @Test
    fun `addReport with image writes attachment using injected context`() = runTest {
        val oleDir = Files.createTempDirectory("enterprises_ole").toFile()
        mockkObject(FileUtils)
        every { FileUtils.getOlePath(context) } returns "${oleDir.absolutePath}/"
        every { timeProvider.now() } returns 12345L
        coEvery { teamDao.getById(any()) } returns MyTeam().apply { _id = "report-1" }

        val imageBytes = byteArrayOf(1, 2, 3, 4)
        val report = FinanceReportParams(
            description = "desc",
            beginningBalance = 0,
            sales = 0,
            otherIncome = 0,
            wages = 0,
            otherExpenses = 0,
            startDate = 0L,
            endDate = 0L,
            teamId = "team-1",
            teamType = "team",
            teamPlanetCode = "code",
            imageName = "logo.png",
            imageData = imageBytes,
        )

        repository.addReport(report)

        // The injected context must flow into FileUtils.getOlePath (mocked above) so the
        // attachment lands under our temp dir; the report id is a random UUID, so locate
        // the written file by name within the ole tree.
        val writtenFile = oleDir.walkTopDown().firstOrNull { it.name == "logo.png" }
        assertTrue("attachment file should have been written via injected context", writtenFile != null)
        assertArrayEquals(imageBytes, writtenFile!!.readBytes())

        coVerify { teamDao.upsert(any()) }
    }

    @Test
    fun `getReportsFlow deduplicates byte-identical emissions`() = runTest {
        val r1 = report("r1", "rev1", sales = 10)
        val r2 = report("r1", "rev1", sales = 10)
        every { teamDao.observeNonArchivedReportsByTeamId("team1") } returns
            flowOf(listOf(r1), listOf(r2))

        val emissions = mutableListOf<List<MyTeam>>()
        repository.getReportsFlow("team1").collect { emissions.add(it) }

        assertEquals(1, emissions.size)
    }

    @Test
    fun `getReportsFlow emits when a locally-edited financial field changes with rev unchanged`() = runTest {
        val before = report("r1", "rev1", sales = 10)
        val after = report("r1", "rev1", sales = 25)
        every { teamDao.observeNonArchivedReportsByTeamId("team1") } returns
            flowOf(listOf(before), listOf(after))

        val emissions = mutableListOf<List<MyTeam>>()
        repository.getReportsFlow("team1").collect { emissions.add(it) }

        assertEquals(2, emissions.size)
        assertEquals(10, emissions[0][0].sales)
        assertEquals(25, emissions[1][0].sales)
    }

    @Test
    fun `getReportsFlow emits when description changes with id and rev unchanged`() = runTest {
        val before = report("r1", "rev1", description = "old")
        val after = report("r1", "rev1", description = "new")
        every { teamDao.observeNonArchivedReportsByTeamId("team1") } returns
            flowOf(listOf(before), listOf(after))

        val emissions = mutableListOf<List<MyTeam>>()
        repository.getReportsFlow("team1").collect { emissions.add(it) }

        assertEquals(2, emissions.size)
        assertEquals("old", emissions[0][0].description)
        assertEquals("new", emissions[1][0].description)
    }

    @Test
    fun `getReportsFlow emits when a report is added`() = runTest {
        val r1 = report("r1", "rev1", createdDate = 100L)
        val r2 = report("r2", "rev2", createdDate = 200L)
        every { teamDao.observeNonArchivedReportsByTeamId("team1") } returns
            flowOf(listOf(r1), listOf(r1, r2))

        val emissions = mutableListOf<List<MyTeam>>()
        repository.getReportsFlow("team1").collect { emissions.add(it) }

        assertEquals(2, emissions.size)
        assertEquals(1, emissions[0].size)
        assertEquals(2, emissions[1].size)
    }

    // Archived-report filtering and createdDate ordering now live in the DAO query
    // (TeamDao.observeNonArchivedReportsByTeamId), so they are covered by TeamDaoTest
    // against a real database. What the repository still owns is handing the rows on
    // in the order the query returned them.
    @Test
    fun `getReportsFlow preserves the order returned by the dao`() = runTest {
        val r2 = report("r2", "rev2", createdDate = 200L)
        val r1 = report("r1", "rev1", createdDate = 100L)
        every { teamDao.observeNonArchivedReportsByTeamId("team1") } returns
            flowOf(listOf(r2, r1))

        val emissions = mutableListOf<List<MyTeam>>()
        repository.getReportsFlow("team1").collect { emissions.add(it) }

        assertEquals(1, emissions.size)
        assertEquals(listOf("r2", "r1"), emissions[0].map { it._id })
    }

    private fun report(
        id: String,
        rev: String,
        description: String? = null,
        sales: Int = 0,
        createdDate: Long = 0L,
        status: String? = null,
    ) = MyTeam().apply {
        _id = id
        _rev = rev
        docType = "report"
        this.description = description
        this.sales = sales
        this.createdDate = createdDate
        this.status = status
    }
}
