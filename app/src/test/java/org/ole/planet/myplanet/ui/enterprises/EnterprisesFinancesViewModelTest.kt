package org.ole.planet.myplanet.ui.enterprises

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.Transaction
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule

class EnterprisesFinancesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var teamsRepository: TeamsRepository
    private lateinit var viewModel: EnterprisesFinancesViewModel
    private val testDispatcher = mainDispatcherRule.testDispatcher

    @Before
    fun setup() {
        teamsRepository = mockk()
        viewModel = EnterprisesFinancesViewModel(teamsRepository)
    }

    @Test
    fun `getTeamTransactions updates transactions state`() = runTest {
        val mockTransactions = listOf(Transaction("1", 0L, "desc", "type", 100, 100))
        val teamId = "test_team_id"
        val sortAscending = true
        val startDate = 1000L
        val endDate = 2000L

        coEvery {
            teamsRepository.getTeamTransactionsWithBalance(
                teamId = teamId,
                startDate = startDate,
                endDate = endDate,
                sortAscending = sortAscending
            )
        } returns flowOf(mockTransactions)

        viewModel.getTeamTransactions(teamId, sortAscending, startDate, endDate)
        testDispatcher.scheduler.advanceUntilIdle()

        val actualTransactions = viewModel.transactions.first()
        assertEquals(mockTransactions, actualTransactions)
    }
}
