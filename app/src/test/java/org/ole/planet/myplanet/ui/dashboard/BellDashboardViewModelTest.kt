package org.ole.planet.myplanet.ui.dashboard

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.TestTimeProvider

@OptIn(ExperimentalCoroutinesApi::class)
class BellDashboardViewModelTest {

    private val progressRepository: ProgressRepository = mockk(relaxed = true)
    private val teamsRepository: TeamsRepository = mockk(relaxed = true)
    private val surveysRepository: SurveysRepository = mockk(relaxed = true)
    private val submissionsRepository: SubmissionsRepository = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val coursesRepository: CoursesRepository = mockk(relaxed = true)
    private val timeProvider = TestTimeProvider(1_700_000_000_000L)

    private lateinit var viewModel: BellDashboardViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        io.mockk.mockkObject(NetworkUtils)
        every { NetworkUtils.isNetworkConnectedFlow } returns MutableStateFlow(true)
        every { surveysRepository.dueRemindersFlow() } returns flowOf()

        viewModel = BellDashboardViewModel(
            progressRepository, teamsRepository, surveysRepository,
            submissionsRepository, userRepository, coursesRepository, timeProvider
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        io.mockk.unmockkAll()
    }

    @Test
    fun `checkPendingSurveys drops if interval guard blocks`() = runTest {
        coEvery { surveysRepository.getLastSurveyDialogShown() } returns timeProvider.now() - TimeUnit.MINUTES.toMillis(30)

        val emitted = mutableListOf<SurveyPrompt?>()
        val job = launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { viewModel.surveyPrompt.collect { emitted.add(it) } }

        viewModel.checkPendingSurveys("user1")
        advanceUntilIdle()

        assertEquals(0, emitted.size)
        job.cancel()
    }

    @Test
    fun `checkPendingSurveys drops if reminder scheduled`() = runTest {
        val submission = Submission().apply { id = "survey1" }
        coEvery { surveysRepository.getLastSurveyDialogShown() } returns timeProvider.now() - TimeUnit.HOURS.toMillis(2)
        coEvery { submissionsRepository.getUniquePendingSurveys("user1") } returns listOf(submission)
        coEvery { surveysRepository.isReminderScheduled("survey1") } returns true

        val emitted = mutableListOf<SurveyPrompt?>()
        val job = launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { viewModel.surveyPrompt.collect { emitted.add(it) } }

        viewModel.checkPendingSurveys("user1")
        advanceUntilIdle()

        assertEquals(0, emitted.size)
        job.cancel()
    }

    @Test
    fun `checkPendingSurveys emits correctly`() = runTest {
        val submission = Submission().apply { id = "survey1" }
        coEvery { surveysRepository.getLastSurveyDialogShown() } returns timeProvider.now() - TimeUnit.HOURS.toMillis(2)
        coEvery { submissionsRepository.getUniquePendingSurveys("user1") } returns listOf(submission)
        coEvery { surveysRepository.isReminderScheduled("survey1") } returns false
        coEvery { submissionsRepository.getSurveyTitlesFromSubmissions(any()) } returns listOf("Survey 1")

        val emitted = mutableListOf<SurveyPrompt?>()
        val job = launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { viewModel.surveyPrompt.collect { emitted.add(it) } }

        viewModel.checkPendingSurveys("user1")
        advanceUntilIdle()

        assertEquals(1, emitted.size)
        assertEquals(false, emitted[0]?.isReminder)
        job.cancel()
    }

    @Test
    fun `handleDueReminders emits multiple groups`() = runTest {
        val submission1 = Submission().apply { id = "sub1"; status = "pending" }
        val submission2 = Submission().apply { id = "sub2"; status = "completed" }
        val submission3 = Submission().apply { id = "sub3"; status = "pending" }

        every { surveysRepository.dueRemindersFlow() } returns flowOf(listOf("sub1,sub2", "sub3"))
        coEvery { submissionsRepository.getSubmissionsByIds(any()) } returns listOf(submission1, submission2, submission3)
        coEvery { submissionsRepository.getSurveyTitlesFromSubmissions(any()) } returns listOf("Title")

        viewModel = BellDashboardViewModel(
            progressRepository, teamsRepository, surveysRepository,
            submissionsRepository, userRepository, coursesRepository, timeProvider
        )

        val emitted = mutableListOf<SurveyPrompt?>()
        val job = launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { viewModel.surveyPrompt.collect { emitted.add(it) } }

        advanceUntilIdle()

        assertEquals(2, emitted.size)
        assertEquals(true, emitted[0]?.isReminder)
        assertEquals(true, emitted[1]?.isReminder)

        job.cancel()
    }

    @Test
    fun `handleDueReminders deduplicates ids in single pass preserving order and handles empty groups`() = runTest {
        val submission1 = Submission().apply { id = "sub1"; status = "pending" }
        val submission2 = Submission().apply { id = "sub2"; status = "pending" }

        val capturedIds = mutableListOf<List<String>>()
        every { surveysRepository.dueRemindersFlow() } returns flowOf(listOf("sub1,sub2", "sub1,", "  ", "sub2"))
        coEvery { submissionsRepository.getSubmissionsByIds(capture(capturedIds)) } returns listOf(submission1, submission2)
        coEvery { submissionsRepository.getSurveyTitlesFromSubmissions(any()) } returns listOf("Title")

        viewModel = BellDashboardViewModel(
            progressRepository, teamsRepository, surveysRepository,
            submissionsRepository, userRepository, coursesRepository, timeProvider
        )

        val emitted = mutableListOf<SurveyPrompt?>()
        val job = launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { viewModel.surveyPrompt.collect { emitted.add(it) } }

        advanceUntilIdle()

        assertEquals(1, capturedIds.size)
        assertEquals(listOf("sub1", "sub2"), capturedIds[0])

        job.cancel()
    }
}
