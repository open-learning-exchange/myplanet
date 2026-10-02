package org.ole.planet.myplanet.ui.enterprises

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.FinanceReport
import org.ole.planet.myplanet.repository.EnterprisesRepository
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.TimeProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class EnterprisesViewModelTest {

    private lateinit var enterprisesRepository: EnterprisesRepository
    private lateinit var viewModel: EnterprisesViewModel

    private fun createReport(id: String) = FinanceReport(
        _id = id,
        _rev = "rev1",
        status = "active",
        description = "desc",
        beginningBalance = 0,
        sales = 0,
        otherIncome = 0,
        wages = 0,
        otherExpenses = 0,
        startDate = 1000L,
        endDate = 2000L,
        createdDate = 500L,
        updatedDate = 600L,
        updated = false,
        imageName = null
    )

    @Before
    fun setUp() {
        enterprisesRepository = mockk(relaxed = true)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val timeProvider = mockk<TimeProvider>()
        val testDispatcher = StandardTestDispatcher()
        val dispatcherProvider = TestDispatcherProvider(testDispatcher)
        viewModel = EnterprisesViewModel(
            enterprisesRepository = enterprisesRepository,
            appScope = TestScope(testDispatcher),
            context = context,
            timeProvider = timeProvider,
            dispatcherProvider = dispatcherProvider
        )
    }

    @Test
    fun `getReportsFlow returns flow from repository non-suspendingly`() {
        val teamId = "team123"
        val expectedReports = listOf(createReport("report1"))
        val flow = flowOf(expectedReports)

        every { enterprisesRepository.getReportsFlow(teamId) } returns flow

        val resultFlow = viewModel.getReportsFlow(teamId)

        assertEquals(flow, resultFlow)
        verify(exactly = 1) { enterprisesRepository.getReportsFlow(teamId) }
    }

    @Test
    fun `exportReportsAsCsv delegates to repository as suspend function`() = runTest {
        val teamId = "team123"
        val teamName = "Team Alpha"
        val expectedCsv = "CSV Content"

        coEvery { enterprisesRepository.exportReportsAsCsv(teamId, teamName) } returns expectedCsv

        val resultCsv = viewModel.exportReportsAsCsv(teamId, teamName)

        assertEquals(expectedCsv, resultCsv)
        coVerify(exactly = 1) { enterprisesRepository.exportReportsAsCsv(teamId, teamName) }
    }
}
