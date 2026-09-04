package org.ole.planet.myplanet.ui.enterprises

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.repository.EnterprisesRepository

class EnterprisesViewModelTest {

    private lateinit var enterprisesRepository: EnterprisesRepository
    private lateinit var viewModel: EnterprisesViewModel

    @Before
    fun setUp() {
        enterprisesRepository = mockk(relaxed = true)
        viewModel = EnterprisesViewModel(enterprisesRepository)
    }

    @Test
    fun `getReportsFlow returns flow from repository non-suspendingly`() {
        val teamId = "team123"
        val expectedReports = listOf(MyTeam().apply { _id = "report1" })
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
