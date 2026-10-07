package org.ole.planet.myplanet.ui.enterprises

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.FinanceReport
import org.ole.planet.myplanet.repository.EnterprisesRepository
import org.ole.planet.myplanet.utils.AttachmentReader
import org.ole.planet.myplanet.utils.FileUtils
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
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

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
        val dispatcherProvider = TestDispatcherProvider(testDispatcher)
        viewModel = EnterprisesViewModel(
            enterprisesRepository = enterprisesRepository,
            appScope = testScope,
            attachmentReader = AttachmentReader(context, timeProvider, dispatcherProvider)
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

    @Test
    fun `addReport emits ReportEvent Error when attachment read throws`() = runTest {
        val mockUri = mockk<Uri>()
        val error = SecurityException("Permission denied")

        mockkObject(FileUtils)
        try {
            every { FileUtils.getDisplayName(any(), any(), any()) } throws error

            val events = mutableListOf<ReportEvent>()
            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.reportEvent.collect { events.add(it) }
            }

            viewModel.addReport(
                description = "desc",
                beginningBalance = 100,
                sales = 200,
                otherIncome = 50,
                wages = 30,
                otherExpenses = 20,
                startDate = 1000L,
                endDate = 2000L,
                teamId = "team1",
                teamType = "type",
                teamPlanetCode = "planet",
                imageUri = mockUri
            )
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(1, events.size)
            assertEquals(ReportEvent.Error("Failed to add report. Please try again."), events[0])
            job.cancel()
        } finally {
            unmockkObject(FileUtils)
        }
    }
}
