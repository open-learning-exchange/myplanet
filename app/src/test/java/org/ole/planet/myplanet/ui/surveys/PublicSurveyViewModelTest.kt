package org.ole.planet.myplanet.ui.surveys

import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.SurveysRepository

@OptIn(ExperimentalCoroutinesApi::class)
class PublicSurveyViewModelTest {

    private lateinit var surveysRepository: SurveysRepository
    private lateinit var submissionsRepository: SubmissionsRepository
    private lateinit var payloadBuilder: PublicSurveyPayloadBuilder
    private lateinit var viewModel: PublicSurveyViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        surveysRepository = mockk(relaxed = true)
        submissionsRepository = mockk(relaxed = true)
        payloadBuilder = PublicSurveyPayloadBuilder()

        viewModel = PublicSurveyViewModel(
            surveysRepository,
            submissionsRepository,
            payloadBuilder
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test loadSurvey success updates state and saves survey`() = runTest {
        val surveyDoc = JsonObject().apply { addProperty("_id", "survey123") }
        val response = JsonObject().apply { add("survey", surveyDoc) }

        coEvery { surveysRepository.fetchPublicSurvey("http://base", "team1", "survey123") } returns response

        viewModel.loadSurvey("http://base", "team1", "survey123")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.loadState.value
        assertTrue(state is PublicSurveyViewModel.SurveyLoadState.Success)
        assertEquals(surveyDoc, (state as PublicSurveyViewModel.SurveyLoadState.Success).surveyDoc)
        coVerify { surveysRepository.saveSurveyFromPublicApi(surveyDoc) }
    }

    @Test
    fun `test loadSurvey error updates state to Error`() = runTest {
        coEvery { surveysRepository.fetchPublicSurvey("http://base", "team1", "survey123") } returns null

        viewModel.loadSurvey("http://base", "team1", "survey123")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.loadState.value
        assertTrue(state is PublicSurveyViewModel.SurveyLoadState.Error)
    }

    @Test
    fun `test uploadCompletedSubmission skips upload when no submission found`() = runTest {
        coEvery { submissionsRepository.getLatestSubmissionByParentId("survey123", "complete") } returns null

        var emittedEvent: PublicSurveyViewModel.UploadEvent? = null
        val collectJob = launch(testDispatcher) {
            emittedEvent = viewModel.uploadEvents.first()
        }

        viewModel.uploadCompletedSubmission("http://base", "team1", "survey123", 1000L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(PublicSurveyViewModel.UploadEvent.NavigateOnward, emittedEvent)
        collectJob.cancel()
    }

    @Test
    fun `test uploadCompletedSubmission builds payload and submits survey`() = runTest {
        val submission = Submission().apply {
            id = "sub1"
            lastUpdateTime = 2000L
            user = "{\"name\":\"John\", \"age\":\"30\"}"
        }
        val questions = listOf(ExamQuestion().apply { id = "q1"; type = "text" })

        coEvery { submissionsRepository.getLatestSubmissionByParentId("survey123", "complete") } returns submission
        coEvery { surveysRepository.getExamQuestions("survey123") } returns questions
        coEvery { surveysRepository.submitPublicSurvey("http://base", "team1", "survey123", any(), any()) } returns true

        var emittedEvent: PublicSurveyViewModel.UploadEvent? = null
        val collectJob = launch(testDispatcher) {
            emittedEvent = viewModel.uploadEvents.first()
        }

        viewModel.uploadCompletedSubmission("http://base", "team1", "survey123", 1000L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(emittedEvent is PublicSurveyViewModel.UploadEvent.ShowToastAndNavigate)
        assertEquals(R.string.survey_submitted, (emittedEvent as PublicSurveyViewModel.UploadEvent.ShowToastAndNavigate).messageResId)

        coVerify {
            surveysRepository.submitPublicSurvey(
                "http://base",
                "team1",
                "survey123",
                any(),
                match { it.get("age").asInt == 30 }
            )
        }
        collectJob.cancel()
    }
}
