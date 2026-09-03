package org.ole.planet.myplanet.repository

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.dao.ExamDao
import org.ole.planet.myplanet.data.room.dao.QuestionDao
import org.ole.planet.myplanet.data.room.dao.SubmissionDao
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.TestTimeProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SurveysRepositoryImplTest {
    private lateinit var repository: SurveysRepositoryImpl
    private lateinit var context: Context
    private lateinit var userSessionManager: UserSessionManager
    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var dispatcherProvider: DispatcherProvider
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var sharedPreferencesEditor: SharedPreferences.Editor
    private val timeProvider = TestTimeProvider(currentTime = 1_700_000_000_000L)
    private val apiInterface: org.ole.planet.myplanet.data.api.ApiInterface = mockk(relaxed = true)
    private val serverUrlMapper: org.ole.planet.myplanet.services.sync.ServerUrlMapper = mockk(relaxed = true)
    private val examDao: ExamDao = mockk(relaxed = true)
    private val questionDao: QuestionDao = mockk(relaxed = true)
    private val submissionDao: SubmissionDao = mockk(relaxed = true)
    private val teamsRepository: TeamsRepository = mockk(relaxed = true)

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        userSessionManager = mockk(relaxed = true)
        sharedPrefManager = mockk(relaxed = true)
        dispatcherProvider = mockk(relaxed = true)
        every { dispatcherProvider.io } returns Dispatchers.Unconfined

        sharedPreferences = mockk(relaxed = true)
        sharedPreferencesEditor = mockk(relaxed = true)
        every { context.getSharedPreferences("survey_reminders", Context.MODE_PRIVATE) } returns sharedPreferences
        every { sharedPreferences.edit() } returns sharedPreferencesEditor
        every { sharedPreferencesEditor.putLong(any(), any()) } returns sharedPreferencesEditor
        every { sharedPreferencesEditor.putString(any(), any()) } returns sharedPreferencesEditor
        every { sharedPreferencesEditor.remove(any()) } returns sharedPreferencesEditor

        repository = SurveysRepositoryImpl(
            context,
            apiInterface,
            serverUrlMapper,
            userSessionManager,
            sharedPrefManager,
            dispatcherProvider,
            timeProvider,
            examDao,
            questionDao,
            submissionDao,
            { teamsRepository }
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getExamQuestions filters by examId`() = runTest {
        coEvery { questionDao.getByExamId("exam1") } returns listOf(ExamQuestion(id = "q1", examId = "exam1"))

        val result = repository.getExamQuestions("exam1")

        assertEquals(listOf("exam1"), result.map { it.examId })
    }

    @Test
    fun `getSurveys returns all surveys`() = runTest {
        coEvery { examDao.getByType("surveys") } returns listOf(StepExam(id = "survey1", type = "surveys"))

        val result = repository.getSurveys()

        assertEquals(listOf("survey1"), result.map { it.id })
    }

    @Test
    fun `getTeamOwnedSurveys returns empty list when teamId is null`() = runTest {
        val result = repository.getTeamOwnedSurveys(null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getTeamOwnedSurveys returns filtered surveys for valid teamId`() = runTest {
        val teamId = "team1"
        // Mock team submissions
        coEvery { submissionDao.getByTeamId(teamId) } returns listOf(
            Submission(id = "sub1", parent = "{\"_id\":\"exam1\"}")
        )
        // Mock adopted source survey ids
        coEvery { examDao.getByTeamIdAndType(teamId, "surveys") } returns listOf(
            StepExam(id = "survey1", sourceSurveyId = "source1")
        )
        coEvery { examDao.getTeamOwnedSurveys(teamId, setOf("exam1")) } returns listOf(
            StepExam(id = "exam1", teamId = "other"),
            StepExam(id = "survey2", teamId = teamId)
        )

        val result = repository.getTeamOwnedSurveys(teamId)

        assertEquals(listOf("exam1", "survey2"), result.map { it.id })
    }

    @Test
    fun `getAdoptableTeamSurveys returns empty list when teamId is null`() = runTest {
        val result = repository.getAdoptableTeamSurveys(null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAdoptableTeamSurveys returns unadopted shareable surveys`() = runTest {
        val teamId = "team1"
        // Mock team submissions
        coEvery { submissionDao.getByTeamId(teamId) } returns listOf(
            Submission(id = "sub1", parent = "{\"_id\":\"exam1\"}")
        )
        // Mock adopted source survey ids
        coEvery { examDao.getByTeamIdAndType(teamId, "surveys") } returns listOf(
            StepExam(id = "survey1", sourceSurveyId = "source1")
        )
        coEvery { examDao.getAdoptableTeamSurveys(setOf("exam1", "source1")) } returns listOf(
            StepExam(id = "survey2", isTeamShareAllowed = true)
        )

        val result = repository.getAdoptableTeamSurveys(teamId)

        assertEquals(listOf("survey2"), result.map { it.id })
    }

    @Test
    fun `getIndividualSurveys filters surveys without team and not shareable`() = runTest {
        coEvery { examDao.getByType("surveys") } returns listOf(
            StepExam(id = "survey1", isTeamShareAllowed = false, teamId = null), // Included
            StepExam(id = "survey2", isTeamShareAllowed = true, teamId = null), // Excluded (shareable)
            StepExam(id = "survey3", isTeamShareAllowed = false, teamId = "team1"), // Excluded (has teamId)
            StepExam(id = "survey4", isTeamShareAllowed = false, teamId = "") // Included (empty teamId)
        )

        val result = repository.getIndividualSurveys()

        assertEquals(listOf("survey1", "survey4"), result.map { it.id })
    }

    @Test
    fun `getSurvey returns survey by id`() = runTest {
        coEvery { examDao.getById("survey1") } returns StepExam(id = "survey1")

        val result = repository.getSurvey("survey1")

        assertEquals("survey1", result?.id)
    }

    @Test
    fun `getSurvey returns survey by name if id not found`() = runTest {
        coEvery { examDao.getById("Survey Name") } returns null
        coEvery { examDao.getByTypeAndName("surveys", "Survey Name") } returns StepExam(id = "survey2", name = "Survey Name")

        val result = repository.getSurvey("Survey Name")

        assertEquals("survey2", result?.id)
    }

    @Test
    fun `getSurvey returns null if no match found`() = runTest {
        coEvery { examDao.getById("Survey Name") } returns null
        coEvery { examDao.getByTypeAndName("surveys", "Survey Name") } returns null

        val result = repository.getSurvey("Survey Name")

        assertEquals(null, result)
    }

    @Test
    fun `getPendingAdoptedSurveys returns pending adoptions`() = runTest {
        coEvery { examDao.getPendingAdoptedSurveys() } returns listOf(
            StepExam(id = "survey1"),
            StepExam(id = "survey2")
        )

        val result = repository.getPendingAdoptedSurveys()

        assertEquals(listOf("survey1", "survey2"), result.map { it.id })
    }

    @Test
    fun `getSurveySubmissionCount uses pending surveys dao query`() = runTest {
        coEvery { submissionDao.countPendingSurveys("user1") } returns 2

        val count = repository.getSurveySubmissionCount("user1")

        assertEquals(2, count)
    }

    @Test
    fun `getLastSurveyDialogShown returns stored value`() = runTest {
        every { sharedPreferences.getLong("last_survey_dialog_shown", 0L) } returns 12345L

        val result = repository.getLastSurveyDialogShown()

        assertEquals(12345L, result)
        verify { sharedPreferences.getLong("last_survey_dialog_shown", 0L) }
    }

    @Test
    fun `setLastSurveyDialogShown stores value`() = runTest {
        repository.setLastSurveyDialogShown(12345L)

        verify { sharedPreferences.edit() }
        verify { sharedPreferencesEditor.putLong("last_survey_dialog_shown", 12345L) }
        verify { sharedPreferencesEditor.apply() }
    }

    @Test
    fun `isReminderScheduled returns true if scheduled`() = runTest {
        every { sharedPreferences.contains("reminder_time_survey1") } returns true

        val result = repository.isReminderScheduled("survey1")

        assertTrue(result)
    }

    @Test
    fun `isReminderScheduled returns false if not scheduled`() = runTest {
        every { sharedPreferences.contains("reminder_time_survey1") } returns false

        val result = repository.isReminderScheduled("survey1")

        assertFalse(result)
    }

    @Test
    fun `scheduleSurveyReminder writes to SharedPreferences`() = runTest {
        repository.scheduleSurveyReminder("survey1", TimeUnit.DAYS, 1)

        verify { sharedPreferences.edit() }
        verify { sharedPreferencesEditor.putLong(eq("reminder_time_survey1"), any()) }
        verify { sharedPreferencesEditor.putString("reminder_surveys_survey1", "survey1") }
        verify { sharedPreferencesEditor.apply() }
    }




    @Test
    fun `dueRemindersFlow emits due surveys and removes them from preferences`() = runTest {
        // The test setup uses TestTimeProvider with currentTime = 1_700_000_000_000L
        val currentTime = 1_700_000_000_000L
        val allPrefs = mapOf<String, Any>(
            "reminder_time_survey1" to currentTime - 100_000L, // Due
            "reminder_time_survey2" to currentTime + 100_000L  // Not due
        )
        every { sharedPreferences.all } returns allPrefs
        every { sharedPreferences.getLong("reminder_time_survey1", 0) } returns currentTime - 100_000L
        every { sharedPreferences.getLong("reminder_time_survey2", 0) } returns currentTime + 100_000L

        val result = repository.dueRemindersFlow().first()

        assertEquals(listOf("survey1"), result)

        verify { sharedPreferencesEditor.remove("reminder_time_survey1") }
        verify { sharedPreferencesEditor.remove("reminder_surveys_survey1") }

        verify(exactly = 0) { sharedPreferencesEditor.remove("reminder_time_survey2") }
        verify(exactly = 0) { sharedPreferencesEditor.remove("reminder_surveys_survey2") }
    }

    @Test
    fun `getSurveyInfos groups submissions correctly by resolved survey IDs`() = runTest {
        // Setup surveys
        val survey1 = StepExam(id = "survey1", createdDate = 1696161600000L)
        val survey2 = StepExam(id = "survey2", createdDate = 1696248000000L)
        val surveys = listOf(survey1, survey2)

        // Setup submissions with edge cases
        val submissions = listOf(
            // Exact ID
            Submission(id = "sub1", parentId = "survey1", status = "complete", startTime = 1000L),
            // Suffixed ID
            Submission(id = "sub2", parentId = "survey2@some_suffix", status = "requires grading", startTime = 2000L),
            // Unrelated submission
            Submission(id = "sub3", parentId = "unrelated_survey", status = "complete", startTime = 3000L),
            // Incomplete submission
            Submission(id = "sub4", parentId = "survey1", status = "pending", startTime = 4000L),
            // Overlapping-looking ID
            Submission(id = "sub5", parentId = "survey11", status = "complete", startTime = 5000L),
            // Another valid one for survey1
            Submission(id = "sub6", parentId = "survey1@another_suffix", status = "complete", startTime = 6000L)
        )

        val userId = "user1"
        coEvery { submissionDao.getByUserIdWithoutTeam(userId) } returns submissions
        every { context.resources.getQuantityString(any(), any(), any()) } returns "N taken"

        val result = repository.getSurveyInfos(isTeam = false, teamId = null, userId = userId, surveys = surveys)

        // survey1 should have sub1 and sub6
        val info1 = result["survey1"]
        assertEquals("survey1", info1?.surveyId)
        // verify counts logic (we mock getQuantityString but we can assert the map keys)
        assertTrue(result.containsKey("survey1"))

        // survey2 should have sub2
        val info2 = result["survey2"]
        assertEquals("survey2", info2?.surveyId)
        assertTrue(result.containsKey("survey2"))

        // Only 2 surveys were requested
        assertEquals(2, result.size)
    }

}
