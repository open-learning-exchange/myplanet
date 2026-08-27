package org.ole.planet.myplanet.repository

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val dispatcherProvider: DispatcherProvider =
        TestDispatcherProvider(UnconfinedTestDispatcher())

    private val repository = EnterprisesRepositoryImpl(
        context, teamDao, timeProvider, dispatcherProvider
    )

    @After
    fun tearDown() {
        unmockkObject(FileUtils)
    }

    @Test
    fun `exportReportsAsCsv calculates correct profitLoss and endingBalance`() = runTest {
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

        val result = repository.exportReportsAsCsv(listOf(report), "Test Team")

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
}
