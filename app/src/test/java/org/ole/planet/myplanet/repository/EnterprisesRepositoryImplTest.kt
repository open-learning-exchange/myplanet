package org.ole.planet.myplanet.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.TeamDao
import org.ole.planet.myplanet.model.EnterpriseReportCsvProjection
import org.ole.planet.myplanet.model.FinanceReport
import org.ole.planet.myplanet.model.FinanceReportParams
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.utils.AndroidDateFormatter
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.StoragePathResolver
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.TimeProvider
import org.ole.planet.myplanet.utils.TimeUtils

@OptIn(ExperimentalCoroutinesApi::class)
class EnterprisesRepositoryImplTest {

    private val storagePathResolver: StoragePathResolver = mockk(relaxed = true)
    private val teamDao: TeamDao = mockk(relaxed = true)
    private val timeProvider: TimeProvider = mockk(relaxed = true)
    private val dispatcherProvider: DispatcherProvider = TestDispatcherProvider(UnconfinedTestDispatcher())

    private val repository = EnterprisesRepositoryImpl(
        storagePathResolver, teamDao, timeProvider, dispatcherProvider, AndroidDateFormatter()
    )

    @Test
    fun `getReportsFlow delegates to observeNonArchivedReportsByTeamId`() = runTest {
        val teamId = "team123"
        val rawReports = listOf(
            MyTeam().apply {
                _id = "report1"
                createdDate = 2000L
            },
            MyTeam().apply {
                _id = "report2"
                createdDate = 1000L
            }
        )
        val expectedReports = listOf(
            FinanceReport(_id="report1", _rev=null, status=null, description=null, beginningBalance=0, sales=0, otherIncome=0, wages=0, otherExpenses=0, startDate=0L, endDate=0L, createdDate=2000L, updatedDate=0L, updated=false, imageName=null),
            FinanceReport(_id="report2", _rev=null, status=null, description=null, beginningBalance=0, sales=0, otherIncome=0, wages=0, otherExpenses=0, startDate=0L, endDate=0L, createdDate=1000L, updatedDate=0L, updated=false, imageName=null)
        )

        every { teamDao.observeNonArchivedReportsByTeamId(teamId) } returns flowOf(rawReports)

        val result = repository.getReportsFlow(teamId).first()

        assertEquals(expectedReports, result)
        verify(exactly = 1) { teamDao.observeNonArchivedReportsByTeamId(teamId) }
    }

    @Test
    fun `exportReportsAsCsv calculates correct profitLoss and endingBalance`() = runTest {
        val teamId = "team123"
        val reportProjection = EnterpriseReportCsvProjection(
            startDate = 1000L,
            endDate = 2000L,
            createdDate = 3000L,
            updatedDate = 4000L,
            beginningBalance = 100,
            sales = 50,
            otherIncome = 20,
            wages = 10,
            otherExpenses = 15
        )

        coEvery { teamDao.getNonArchivedReportCsvProjectionsByTeamId(teamId) } returns listOf(reportProjection)

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
    fun `addReport with image writes attachment using storagePathResolver`() = runTest {
        val tempDir = Files.createTempDirectory("enterprises_test").toFile()
        val destFile = File(tempDir, "team_attachments/report-1/logo.png")
        every { storagePathResolver.resolveTeamAttachment(any(), "logo.png") } returns destFile
        every { timeProvider.now() } returns 12345L

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

        assertTrue("attachment file should have been written", destFile.exists())
        assertArrayEquals(imageBytes, destFile.readBytes())

        coVerify { teamDao.setImageNameById(any(), "logo.png") }
    }

    @Test
    fun `archiveReport calls archiveById and never upsert`() = runTest {
        repository.archiveReport("r1")

        coVerify(exactly = 1) { teamDao.archiveById("r1") }
        coVerify(exactly = 0) { teamDao.upsert(any()) }
    }

    @Test
    fun `blank reportId in archiveReport calls neither archiveById nor setImageNameById`() = runTest {
        repository.archiveReport("")
        repository.archiveReport("   ")

        coVerify(exactly = 0) { teamDao.archiveById(any()) }
        coVerify(exactly = 0) { teamDao.setImageNameById(any(), any()) }
    }

    @Test
    fun `updateReport calls updateReportFields with payload values and timeProvider now and never getById or upsert`() = runTest {
        every { timeProvider.now() } returns 99999L
        coEvery {
            teamDao.updateReportFields(
                id = any(),
                description = any(),
                beginningBalance = any(),
                sales = any(),
                otherIncome = any(),
                wages = any(),
                otherExpenses = any(),
                startDate = any(),
                endDate = any(),
                updatedDate = any()
            )
        } returns 1

        val payload = FinanceReportParams(
            description = "updated description",
            beginningBalance = 10,
            sales = 20,
            otherIncome = 30,
            wages = 40,
            otherExpenses = 50,
            startDate = 100L,
            endDate = 200L,
            teamId = "team1",
            teamType = "enterprise",
            teamPlanetCode = "planet1",
            imageName = null,
            imageData = null
        )

        repository.updateReport("r1", payload)

        coVerify(exactly = 1) {
            teamDao.updateReportFields(
                id = "r1",
                description = "updated description",
                beginningBalance = 10,
                sales = 20,
                otherIncome = 30,
                wages = 40,
                otherExpenses = 50,
                startDate = 100L,
                endDate = 200L,
                updatedDate = 99999L
            )
        }
        coVerify(exactly = 0) { teamDao.getById(any()) }
        coVerify(exactly = 0) { teamDao.upsert(any()) }
    }

    @Test
    fun `updateReport with blank reportId calls nothing`() = runTest {
        val payload = FinanceReportParams(
            description = "desc",
            beginningBalance = 0,
            sales = 0,
            otherIncome = 0,
            wages = 0,
            otherExpenses = 0,
            startDate = 0L,
            endDate = 0L,
            teamId = "team1",
            teamType = "enterprise",
            teamPlanetCode = "planet1",
            imageName = "image.png",
            imageData = byteArrayOf(1, 2, 3)
        )

        repository.updateReport("", payload)
        repository.updateReport("   ", payload)

        coVerify(exactly = 0) {
            teamDao.updateReportFields(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        }
        coVerify(exactly = 0) { teamDao.getById(any()) }
        coVerify(exactly = 0) { teamDao.upsert(any()) }
        coVerify(exactly = 0) { teamDao.setImageNameById(any(), any()) }
    }

    @Test
    fun `updateReport attaches image when updateReportFields returns 0 and image data is present`() = runTest {
        val tempDir = Files.createTempDirectory("enterprises_test_update").toFile()
        val destFile = File(tempDir, "team_attachments/r1/photo.png")
        every { storagePathResolver.resolveTeamAttachment("r1", "photo.png") } returns destFile
        every { timeProvider.now() } returns 88888L

        coEvery {
            teamDao.updateReportFields(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 0

        val imageBytes = byteArrayOf(5, 6, 7)
        val payload = FinanceReportParams(
            description = "desc",
            beginningBalance = 0,
            sales = 0,
            otherIncome = 0,
            wages = 0,
            otherExpenses = 0,
            startDate = 0L,
            endDate = 0L,
            teamId = "team1",
            teamType = "enterprise",
            teamPlanetCode = "planet1",
            imageName = "photo.png",
            imageData = imageBytes
        )

        repository.updateReport("r1", payload)

        assertTrue("attachment file should be written", destFile.exists())
        assertArrayEquals(imageBytes, destFile.readBytes())

        coVerify(exactly = 1) { teamDao.setImageNameById("r1", "photo.png") }
    }

    @Test
    fun `addReport short-circuits image attachment update when resolveTeamAttachment returns null`() = runTest {
        every { storagePathResolver.resolveTeamAttachment(any(), any()) } returns null
        every { timeProvider.now() } returns 12345L

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
            imageData = byteArrayOf(1, 2, 3),
        )

        repository.addReport(report)

        // The first upsert happens for addReport itself, but teamDao.getById / second upsert for image attachment must not happen
        coVerify(exactly = 0) { teamDao.getById(any()) }
    }

    @Test
    fun `getReportsFlow deduplicates byte-identical emissions`() = runTest {
        val r1 = report("r1", "rev1", sales = 10)
        val r2 = report("r1", "rev1", sales = 10)
        every { teamDao.observeNonArchivedReportsByTeamId("team1") } returns
            flowOf(listOf(r1), listOf(r2))

        val emissions = mutableListOf<List<FinanceReport>>()
        repository.getReportsFlow("team1").collect { emissions.add(it) }

        assertEquals(1, emissions.size)
    }

    @Test
    fun `getReportsFlow emits when a locally-edited financial field changes with rev unchanged`() = runTest {
        val before = report("r1", "rev1", sales = 10)
        val after = report("r1", "rev1", sales = 25)
        every { teamDao.observeNonArchivedReportsByTeamId("team1") } returns
            flowOf(listOf(before), listOf(after))

        val emissions = mutableListOf<List<FinanceReport>>()
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

        val emissions = mutableListOf<List<FinanceReport>>()
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

        val emissions = mutableListOf<List<FinanceReport>>()
        repository.getReportsFlow("team1").collect { emissions.add(it) }

        assertEquals(2, emissions.size)
        assertEquals(1, emissions[0].size)
        assertEquals(2, emissions[1].size)
    }

    @Test
    fun `getReportsFlow preserves the order returned by the dao`() = runTest {
        val r2 = report("r2", "rev2", createdDate = 200L)
        val r1 = report("r1", "rev1", createdDate = 100L)
        every { teamDao.observeNonArchivedReportsByTeamId("team1") } returns
            flowOf(listOf(r2, r1))

        val emissions = mutableListOf<List<FinanceReport>>()
        repository.getReportsFlow("team1").collect { emissions.add(it) }

        assertEquals(1, emissions.size)
        assertEquals(listOf("r2", "r1"), emissions[0].map { it._id })
    }

    @Test
    fun `getReportsFlow emits when createdDate changes`() = runTest {
        val before = report("r1", "rev1", createdDate = 100L)
        val after = report("r1", "rev1", createdDate = 200L)
        every { teamDao.observeNonArchivedReportsByTeamId("team1") } returns
            flowOf(listOf(before), listOf(after))

        val emissions = mutableListOf<List<FinanceReport>>()
        repository.getReportsFlow("team1").collect { emissions.add(it) }

        assertEquals(2, emissions.size)
        assertEquals(100L, emissions[0][0].createdDate)
        assertEquals(200L, emissions[1][0].createdDate)
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
