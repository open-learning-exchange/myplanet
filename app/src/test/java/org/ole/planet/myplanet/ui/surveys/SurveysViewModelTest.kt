package org.ole.planet.myplanet.ui.surveys

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class SurveysViewModelTest {

    private lateinit var surveysRepository: SurveysRepository
    private lateinit var submissionsRepository: SubmissionsRepository
    private lateinit var userRepository: UserRepository
    private lateinit var userSessionManager: UserSessionManager
    private lateinit var viewModel: SurveysViewModel
    private val testDispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = TestDispatcherProvider(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        surveysRepository = mockk()
        submissionsRepository = mockk()
        userRepository = mockk()
        userSessionManager = mockk()

        viewModel = SurveysViewModel(
            surveysRepository,
            submissionsRepository,
            userRepository,
            userSessionManager,
            testDispatcherProvider
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createSurvey(
        id: String,
        name: String,
        createdDate: Long,
        adoptionDate: Long,
        sourceSurveyId: String? = null
    ): StepExam {
        val survey = StepExam()
        survey.id = id
        survey.name = name
        survey.createdDate = createdDate
        survey.adoptionDate = adoptionDate
        survey.sourceSurveyId = sourceSurveyId
        return survey
    }

    private fun stubLoadSurveys(surveys: List<StepExam>) {
        coEvery { surveysRepository.getIndividualSurveys() } returns surveys
        coEvery { userSessionManager.getUserModel() } returns mockk(relaxed = true)
        coEvery { surveysRepository.getSurveyInfos(any(), any(), any(), any()) } returns emptyMap()
        coEvery { surveysRepository.getSurveyFormState(any(), any()) } returns emptyMap()
    }

    @Test
    fun `test sorting defaults to DATE_DESC and switches sort options`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.surveys.collect {} }
        val survey1 = createSurvey("1", "Zebra", 1000L, 0L)
        val survey2 = createSurvey("2", "Apple", 2000L, 0L)
        val survey3 = createSurvey("3", "Banana", 1500L, 0L)

        stubLoadSurveys(listOf(survey1, survey2, survey3))

        viewModel.loadSurveys(false, null, false)
        testDispatcher.scheduler.advanceUntilIdle()

        // Default should be DATE_DESC
        var currentSurveys = viewModel.surveys.value
        assertEquals("2", currentSurveys[0].exam.id)
        assertEquals("3", currentSurveys[1].exam.id)
        assertEquals("1", currentSurveys[2].exam.id)

        // Switch to DATE_ASC
        viewModel.sort(SurveysViewModel.SortOption.DATE_ASC)
        testDispatcher.scheduler.advanceUntilIdle()
        currentSurveys = viewModel.surveys.value
        assertEquals("1", currentSurveys[0].exam.id)
        assertEquals("3", currentSurveys[1].exam.id)
        assertEquals("2", currentSurveys[2].exam.id)

        // Switch to TITLE_ASC
        viewModel.sort(SurveysViewModel.SortOption.TITLE_ASC)
        testDispatcher.scheduler.advanceUntilIdle()
        currentSurveys = viewModel.surveys.value
        assertEquals("2", currentSurveys[0].exam.id) // Apple
        assertEquals("3", currentSurveys[1].exam.id) // Banana
        assertEquals("1", currentSurveys[2].exam.id) // Zebra

        // Switch to TITLE_DESC
        viewModel.sort(SurveysViewModel.SortOption.TITLE_DESC)
        testDispatcher.scheduler.advanceUntilIdle()
        currentSurveys = viewModel.surveys.value
        assertEquals("1", currentSurveys[0].exam.id) // Zebra
        assertEquals("3", currentSurveys[1].exam.id) // Banana
        assertEquals("2", currentSurveys[2].exam.id) // Apple
    }

    @Test
    fun `test toggleTitleSort correctly toggles between TITLE_ASC and TITLE_DESC`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.surveys.collect {} }
        val survey1 = createSurvey("1", "Zebra", 1000L, 0L)
        val survey2 = createSurvey("2", "Apple", 2000L, 0L)

        stubLoadSurveys(listOf(survey1, survey2))

        viewModel.loadSurveys(false, null, false)
        testDispatcher.scheduler.advanceUntilIdle()

        // Toggle from default (DATE_DESC) -> TITLE_ASC
        viewModel.toggleTitleSort()
        testDispatcher.scheduler.advanceUntilIdle()
        var currentSurveys = viewModel.surveys.value
        assertEquals("2", currentSurveys[0].exam.id) // Apple

        // Toggle again -> TITLE_DESC
        viewModel.toggleTitleSort()
        testDispatcher.scheduler.advanceUntilIdle()
        currentSurveys = viewModel.surveys.value
        assertEquals("1", currentSurveys[0].exam.id) // Zebra
    }

    @Test
    fun `test date ordering logic prioritizes adoptionDate if sourceSurveyId is not null`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.surveys.collect {} }
        val survey1 = createSurvey("1", "A", 1000L, 0L, null)
        val survey2 = createSurvey("2", "B", 500L, 3000L, "src2")
        val survey3 = createSurvey("3", "C", 2000L, 0L, "src3")

        stubLoadSurveys(listOf(survey1, survey2, survey3))

        viewModel.loadSurveys(false, null, false)
        testDispatcher.scheduler.advanceUntilIdle()

        val currentSurveys = viewModel.surveys.value
        assertEquals("2", currentSurveys[0].exam.id)
        assertEquals("3", currentSurveys[1].exam.id)
        assertEquals("1", currentSurveys[2].exam.id)
    }

    @Test
    fun `test normalized search behavior with diacritics and multi-tokens`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.surveys.collect {} }
        val survey1 = createSurvey("1", "El niño is here", 1000L, 0L)
        val survey2 = createSurvey("2", "The dog barks", 2000L, 0L)
        val survey3 = createSurvey("3", "Café au lait", 1500L, 0L)

        stubLoadSurveys(listOf(survey1, survey2, survey3))

        viewModel.loadSurveys(false, null, false)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.search("niño")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.surveys.value.size)
        assertEquals("1", viewModel.surveys.value[0].exam.id)

        viewModel.search("nino")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.surveys.value.size)
        assertEquals("1", viewModel.surveys.value[0].exam.id)

        viewModel.search("CAFE")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.surveys.value.size)
        assertEquals("3", viewModel.surveys.value[0].exam.id)

        viewModel.search("lait cafe")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.surveys.value.size)
        assertEquals("3", viewModel.surveys.value[0].exam.id)

        viewModel.search("The dog")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.surveys.value.size)
        assertEquals("2", viewModel.surveys.value[0].exam.id)
    }
}
