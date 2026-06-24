package org.ole.planet.myplanet.ui.enterprises

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.MainDispatcherRule
import org.ole.planet.myplanet.model.Transaction
import org.ole.planet.myplanet.repository.TeamsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class EnterprisesFinancesViewModelTest {

    private lateinit var teamsRepository: TeamsRepository
    private lateinit var viewModel: EnterprisesFinancesViewModel
    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

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

    @Test
    fun `addTransaction returns NoteRequired when note is empty`() = runTest {
        val result = viewModel.addTransaction(
            teamId = "teamId",
            type = "type",
            note = "",
            amountString = "100",
            dateMillis = 1000L,
            parentCode = "parent",
            planetCode = "planet"
        )
        assertEquals(TransactionResult.NoteRequired, result)
    }

    @Test
    fun `addTransaction returns AmountRequired when amount is empty`() = runTest {
        val result = viewModel.addTransaction(
            teamId = "teamId",
            type = "type",
            note = "note",
            amountString = "",
            dateMillis = 1000L,
            parentCode = "parent",
            planetCode = "planet"
        )
        assertEquals(TransactionResult.AmountRequired, result)
    }

    @Test
    fun `addTransaction returns AmountRequired when amount is invalid`() = runTest {
        val result = viewModel.addTransaction(
            teamId = "teamId",
            type = "type",
            note = "note",
            amountString = "abc",
            dateMillis = 1000L,
            parentCode = "parent",
            planetCode = "planet"
        )
        assertEquals(TransactionResult.AmountRequired, result)
    }

    @Test
    fun `addTransaction returns DateRequired when date is null`() = runTest {
        val result = viewModel.addTransaction(
            teamId = "teamId",
            type = "type",
            note = "note",
            amountString = "100",
            dateMillis = null,
            parentCode = "parent",
            planetCode = "planet"
        )
        assertEquals(TransactionResult.DateRequired, result)
    }

    @Test
    fun `addTransaction returns Success when repository succeeds`() = runTest {
        coEvery {
            teamsRepository.createTransaction(
                teamId = "teamId",
                type = "type",
                note = "note",
                amount = 100,
                date = 1000L,
                parentCode = "parent",
                planetCode = "planet"
            )
        } returns Result.success(Unit)

        val result = viewModel.addTransaction(
            teamId = "teamId",
            type = "type",
            note = "note",
            amountString = "100",
            dateMillis = 1000L,
            parentCode = "parent",
            planetCode = "planet"
        )
        assertEquals(TransactionResult.Success, result)
    }

    @Test
    fun `addTransaction returns Error when repository fails`() = runTest {
        val exception = Exception("Repository error")
        coEvery {
            teamsRepository.createTransaction(
                teamId = "teamId",
                type = "type",
                note = "note",
                amount = 100,
                date = 1000L,
                parentCode = "parent",
                planetCode = "planet"
            )
        } returns Result.failure(exception)

        val result = viewModel.addTransaction(
            teamId = "teamId",
            type = "type",
            note = "note",
            amountString = "100",
            dateMillis = 1000L,
            parentCode = "parent",
            planetCode = "planet"
        )
        assertEquals(TransactionResult.Error("Repository error"), result)
    }
}
