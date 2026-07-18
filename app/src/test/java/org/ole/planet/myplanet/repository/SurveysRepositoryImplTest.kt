package org.ole.planet.myplanet.repository

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import io.mockk.any
import io.mockk.coEvery
import io.mockk.every
import io.mockk.eq
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.dao.legacy.ExamDao
import org.ole.planet.myplanet.data.room.dao.legacy.QuestionDao
import org.ole.planet.myplanet.data.room.dao.legacy.SubmissionDao
import org.ole.planet.myplanet.data.room.dao.legacy.TeamDao
import org.ole.planet.myplanet.data.room.entity.legacy.RoomExamEntity
import org.ole.planet.myplanet.data.room.entity.legacy.RoomQuestionEntity
import org.ole.planet.myplanet.data.room.entity.legacy.RoomSubmissionEntity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.TestTimeProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P], application = Application::class)
class SurveysRepositoryImplTest {
    private lateinit var repository: SurveysRepositoryImpl
    private lateinit var context: Context
    private lateinit var userSessionManager: UserSessionManager
    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var dispatcherProvider: DispatcherProvider
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var sharedPreferencesEditor: SharedPreferences.Editor
    private val timeProvider = TestTimeProvider(currentTime = 1_700_000_000_000L)
    private val examDao: ExamDao = mockk(relaxed = true)
    private val questionDao: QuestionDao = mockk(relaxed = true)
    private val submissionDao: SubmissionDao = mockk(relaxed = true)
    private val teamDao: TeamDao = mockk(relaxed = true)

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
            userSessionManager,
            sharedPrefManager,
            dispatcherProvider,
            timeProvider,
            examDao,
            questionDao,
            submissionDao,
            teamDao
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getExamQuestions filters by examId`() = runTest {
        coEvery { questionDao.getByExamId("exam1") } returns listOf(RoomQuestionEntity(id = "q1", examId = "exam1"))

        val result = repository.getExamQuestions("exam1")

        assertEquals(listOf("exam1"), result.map { it.examId })
    }

    @Test
    fun `getSurveys returns all surveys`() = runTest {
        coEvery { examDao.getByType("surveys") } returns listOf(RoomExamEntity(id = "survey1", type = "surveys"))

        val result = repository.getSurveys()

        assertEquals(listOf("survey1"), result.map { it.id })
    }

    @Test
    fun `getSurveySubmissionCount uses pending surveys dao query`() = runTest {
        coEvery { submissionDao.getPendingSurveys("user1") } returns listOf(
            RoomSubmissionEntity(id = "sub1"),
            RoomSubmissionEntity(id = "sub2")
        )

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
}
