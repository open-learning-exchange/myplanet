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
import org.ole.planet.myplanet.model.Transaction
import android.content.Context
import org.ole.planet.myplanet.repository.TeamsFinancesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.TimeProvider

@OptIn(ExperimentalCoroutinesApi::class)
class EnterprisesFinancesViewModelTest {

    private lateinit var teamsRepository: TeamsFinancesRepository
    private lateinit var dispatcherProvider: DispatcherProvider
    private lateinit var timeProvider: TimeProvider
    private lateinit var context: Context
    private lateinit var viewModel: EnterprisesFinancesViewModel
    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    @Before
    fun setup() {
        teamsRepository = mockk()
        dispatcherProvider = TestDispatcherProvider(testDispatcher)
        timeProvider = mockk()
        context = mockk()
        viewModel = EnterprisesFinancesViewModel(teamsRepository, dispatcherProvider, timeProvider, context)
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
    fun `createTransaction updates transactionCreated channel`() = runTest {
        val teamId = "test_team_id"
        val type = "credit"
        val note = "test note"
        val amount = 100
        val date = 1000L
        val parentCode = "parent"
        val planetCode = "planet"

        coEvery {
            teamsRepository.createTransaction(
                teamId = teamId,
                type = type,
                note = note,
                amount = amount,
                date = date,
                parentCode = parentCode,
                planetCode = planetCode,
                imageName = null,
                imageData = null
            )
        } returns Result.success(Unit)

        viewModel.createTransaction(
            teamId = teamId,
            type = type,
            note = note,
            amount = amount,
            date = date,
            parentCode = parentCode,
            planetCode = planetCode,
            imageUri = null
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val actualResult = viewModel.transactionCreated.first()
        assertEquals(Result.success(Unit), actualResult)
    }
}
