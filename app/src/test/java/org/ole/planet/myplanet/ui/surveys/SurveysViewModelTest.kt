package org.ole.planet.myplanet.ui.surveys

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.services.sync.SyncManager

@OptIn(ExperimentalCoroutinesApi::class)
class SurveysViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var surveysRepository: SurveysRepository
    private lateinit var syncManager: SyncManager
    private lateinit var userSessionManager: UserSessionManager
    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var serverUrlMapper: ServerUrlMapper
    private lateinit var viewModel: SurveysViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        surveysRepository = mockk(relaxed = true)
        syncManager = mockk(relaxed = true)
        userSessionManager = mockk(relaxed = true)
        sharedPrefManager = mockk(relaxed = true)
        serverUrlMapper = mockk(relaxed = true)

        viewModel = SurveysViewModel(
            surveysRepository,
            syncManager,
            userSessionManager,
            sharedPrefManager,
            serverUrlMapper
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createExam(name: String, createdDate: Long): RealmStepExam {
        return RealmStepExam().apply {
            this.name = name
            this.createdDate = createdDate
        }
    }

    @Test
    fun `test filter`() = runTest {
        val exam1 = createExam("Math Exam", 1000)
        val exam2 = createExam("Science Exam", 2000)
        val exam3 = createExam("Advanced Math Survey", 3000)

        val surveysList = listOf(exam1, exam2, exam3)
        coEvery { surveysRepository.getIndividualSurveys() } returns surveysList

        viewModel.loadSurveys(isTeam = false, teamId = null, isTeamShareAllowed = false)
        advanceUntilIdle()

        viewModel.search("math")
        advanceUntilIdle()

        val filteredSurveys = viewModel.surveys.value
        assertEquals(2, filteredSurveys.size)
        assertTrue(filteredSurveys.contains(exam1))
        assertTrue(filteredSurveys.contains(exam3))
    }

    @Test
    fun `test sort`() = runTest {
        val exam1 = createExam("Zebra", 1000)
        val exam2 = createExam("Apple", 2000)
        val exam3 = createExam("Monkey", 3000)

        val surveysList = listOf(exam1, exam2, exam3)
        coEvery { surveysRepository.getIndividualSurveys() } returns surveysList

        viewModel.loadSurveys(isTeam = false, teamId = null, isTeamShareAllowed = false)
        advanceUntilIdle()

        viewModel.sort(SurveysViewModel.SortOption.TITLE_ASC)
        advanceUntilIdle()

        var sortedSurveys = viewModel.surveys.value
        assertEquals("Apple", sortedSurveys[0].name)
        assertEquals("Monkey", sortedSurveys[1].name)
        assertEquals("Zebra", sortedSurveys[2].name)

        viewModel.sort(SurveysViewModel.SortOption.TITLE_DESC)
        advanceUntilIdle()

        sortedSurveys = viewModel.surveys.value
        assertEquals("Zebra", sortedSurveys[0].name)
        assertEquals("Monkey", sortedSurveys[1].name)
        assertEquals("Apple", sortedSurveys[2].name)
    }

    @Test
    fun `test toggleTitleSort`() = runTest {
        val exam1 = createExam("Zebra", 1000)
        val exam2 = createExam("Apple", 2000)
        val exam3 = createExam("Monkey", 3000)

        val surveysList = listOf(exam1, exam2, exam3)
        coEvery { surveysRepository.getIndividualSurveys() } returns surveysList

        viewModel.loadSurveys(isTeam = false, teamId = null, isTeamShareAllowed = false)
        advanceUntilIdle()

        // initially TITLE_ASC based on previous test? default is DATE_DESC
        // when current is DATE_DESC, toggleTitleSort() goes to TITLE_ASC.
        viewModel.toggleTitleSort()
        advanceUntilIdle()

        var sortedSurveys = viewModel.surveys.value
        assertEquals("Apple", sortedSurveys[0].name)
        assertEquals("Monkey", sortedSurveys[1].name)
        assertEquals("Zebra", sortedSurveys[2].name)

        // toggle to TITLE_DESC
        viewModel.toggleTitleSort()
        advanceUntilIdle()

        sortedSurveys = viewModel.surveys.value
        assertEquals("Zebra", sortedSurveys[0].name)
        assertEquals("Monkey", sortedSurveys[1].name)
        assertEquals("Apple", sortedSurveys[2].name)
    }

    @Test
    fun `test normalizeText`() = runTest {
        val exam1 = createExam("Café", 1000)
        val exam2 = createExam("Regular Cafe", 2000)

        val surveysList = listOf(exam1, exam2)
        coEvery { surveysRepository.getIndividualSurveys() } returns surveysList

        viewModel.loadSurveys(isTeam = false, teamId = null, isTeamShareAllowed = false)
        advanceUntilIdle()

        viewModel.search("cafe")
        advanceUntilIdle()

        val filteredSurveys = viewModel.surveys.value
        assertEquals(2, filteredSurveys.size)
        assertTrue(filteredSurveys.contains(exam1))
        assertTrue(filteredSurveys.contains(exam2))
    }
}
