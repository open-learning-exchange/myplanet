package org.ole.planet.myplanet.ui.enterprises

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.Transaction
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule

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
    fun `createTransaction emits success result`() = runTest {
        val teamId = "test_team_id"
        val type = "credit"
        val note = "test note"
        val amount = 100
        val date = 123456789L
        val parentCode = "parent"
        val planetCode = "planet"
        val imageName = "image.png"
        val imageData = byteArrayOf(1, 2, 3)

        coEvery {
            teamsRepository.createTransaction(
                teamId = teamId,
                type = type,
                note = note,
                amount = amount,
                date = date,
                parentCode = parentCode,
                planetCode = planetCode,
                imageName = imageName,
                imageData = imageData
            )
        } returns Result.success(Unit)

        val results = mutableListOf<Result<Unit>>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.transactionCreated.collect { results.add(it) }
        }

        viewModel.createTransaction(
            teamId = teamId,
            type = type,
            note = note,
            amount = amount,
            date = date,
            parentCode = parentCode,
            planetCode = planetCode,
            imageName = imageName,
            imageData = imageData
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, results.size)
        assertEquals(Result.success(Unit), results[0])
        job.cancel()
    }

    @Test
    fun `createTransaction emits failure result`() = runTest {
        val teamId = "test_team_id"
        val type = "credit"
        val note = "test note"
        val amount = 100
        val date = 123456789L
        val error = Exception("Test error")

        coEvery {
            teamsRepository.createTransaction(
                teamId = teamId,
                type = type,
                note = note,
                amount = amount,
                date = date,
                parentCode = null,
                planetCode = null,
                imageName = null,
                imageData = null
            )
        } returns Result.failure(error)

        val results = mutableListOf<Result<Unit>>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.transactionCreated.collect { results.add(it) }
        }

        viewModel.createTransaction(
            teamId = teamId,
            type = type,
            note = note,
            amount = amount,
            date = date,
            parentCode = null,
            planetCode = null,
            imageName = null,
            imageData = null
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, results.size)
        assertEquals(Result.failure<Unit>(error), results[0])
        job.cancel()
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
