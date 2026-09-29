package org.ole.planet.myplanet.ui.surveys

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
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
        surveysRepository = mockk()
        submissionsRepository = mockk()
        payloadBuilder = mockk()

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
    fun `loadSurvey success saves survey and emits SurveyLoaded`() = runTest {
        val response = JsonObject().apply {
            add("survey", JsonObject().apply { addProperty("_id", "survey123") })
        }
        coEvery { surveysRepository.fetchPublicSurvey("url", "team1", "survey123") } returns response
        coEvery { surveysRepository.saveSurveyFromPublicApi(any()) } returns Unit

        val deferredEvent = async { viewModel.events.first() }

        viewModel.loadSurvey("url", "team1", "survey123")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.surveyStarted)
        assertTrue(viewModel.launchTime > 0)
        coVerify { surveysRepository.saveSurveyFromPublicApi(any()) }

        val event = deferredEvent.await()
        assertTrue(event is PublicSurveyEvent.SurveyLoaded)
        assertEquals("survey123", (event as PublicSurveyEvent.SurveyLoaded).surveyId)
    }

    @Test
    fun `loadSurvey null response emits SurveyLoadFailed`() = runTest {
        coEvery { surveysRepository.fetchPublicSurvey("url", "team1", "survey123") } returns null

        val deferredEvent = async { viewModel.events.first() }

        viewModel.loadSurvey("url", "team1", "survey123")
        testDispatcher.scheduler.advanceUntilIdle()

        val event = deferredEvent.await()
        assertTrue(event is PublicSurveyEvent.SurveyLoadFailed)
    }

    @Test
    fun `uploadCompletedSubmission uploads payload and emits UploadFinished`() = runTest {
        val response = JsonObject().apply { addProperty("_id", "survey123") }
        coEvery { surveysRepository.fetchPublicSurvey("url", "team1", "survey123") } returns response
        coEvery { surveysRepository.saveSurveyFromPublicApi(any()) } returns Unit

        val loadEventDeferred = async { viewModel.events.first() }
        viewModel.loadSurvey("url", "team1", "survey123")
        testDispatcher.scheduler.advanceUntilIdle()
        val loadEvent = loadEventDeferred.await()
        assertTrue(loadEvent is PublicSurveyEvent.SurveyLoaded)

        val submission = Submission().apply {
            id = "sub1"
            lastUpdateTime = viewModel.launchTime + 1000L
            user = "{\"name\":\"John\",\"age\":\" 25 \"}"
        }
        coEvery { submissionsRepository.getLatestSubmissionByParentId("survey123", "complete") } returns submission
        val mockAnswers = JsonArray()
        coEvery { payloadBuilder.buildPublicAnswers("survey123", submission) } returns mockAnswers
        coEvery { payloadBuilder.sanitizeRespondent(any()) } answers {
            val user = firstArg<JsonObject>()
            if (user.has("age")) user.addProperty("age", 25)
        }
        coEvery { surveysRepository.submitPublicSurvey("url", "team1", "survey123", mockAnswers, any()) } returns true

        val uploadEventDeferred = async { viewModel.events.first() }

        viewModel.uploadCompletedSubmission("url", "team1", "survey123")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { surveysRepository.submitPublicSurvey("url", "team1", "survey123", mockAnswers, any()) }
        val event = uploadEventDeferred.await()
        assertTrue(event is PublicSurveyEvent.UploadFinished)
        assertTrue((event as PublicSurveyEvent.UploadFinished).success)
    }
}
