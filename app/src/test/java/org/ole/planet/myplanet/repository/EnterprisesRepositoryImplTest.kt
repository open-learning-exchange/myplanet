package org.ole.planet.myplanet.repository

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.TeamDao
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.TimeProvider
import org.ole.planet.myplanet.utils.TimeUtils

@OptIn(ExperimentalCoroutinesApi::class)
class EnterprisesRepositoryImplTest {

    private val teamDao: TeamDao = mockk()
    private val timeProvider: TimeProvider = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider: DispatcherProvider = mockk {
        every { default } returns testDispatcher
        every { io } returns testDispatcher
        every { main } returns testDispatcher
    }

    private val repository = EnterprisesRepositoryImpl(
        teamDao, timeProvider, dispatcherProvider
    )

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
}
