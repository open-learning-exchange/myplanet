package org.ole.planet.myplanet.repository

import android.content.Context
import android.content.SharedPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.DispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class SurveysRepositoryImplTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var repository: SurveysRepositoryImpl
    private lateinit var mockRealm: Realm
    private lateinit var context: Context
    private lateinit var userSessionManager: UserSessionManager
    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var dispatcherProvider: DispatcherProvider
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var sharedPreferencesEditor: SharedPreferences.Editor

    @Before
    fun setup() {
        Logger.getLogger("io.mockk").level = Level.OFF
        mockRealm = mockk(relaxed = true)
        databaseService = mockk(relaxed = true)
        coEvery { databaseService.withRealmAsync<Any>(any()) } answers {
            val operation = firstArg<(Realm) -> Any>()
            operation(mockRealm)
        }
        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val operation = firstArg<(Realm) -> Unit>()
            operation(mockRealm)
        }

        // Properly invoke the transaction block, handling both Realm.Transaction SAMs and Kotlin lambdas
        every { mockRealm.executeTransaction(any()) } answers {
            val arg = it.invocation.args[0]
            try {
                val methods = arg!!.javaClass.declaredMethods
                val executeMethod = methods.find { m -> m.name == "execute" }
                if (executeMethod != null) {
                    executeMethod.isAccessible = true
                    executeMethod.invoke(arg, mockRealm)
                } else {
                    val operation = arg as (Realm) -> Unit
                    operation(mockRealm)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        context = mockk(relaxed = true)
        userSessionManager = mockk(relaxed = true)
        sharedPrefManager = mockk(relaxed = true)
        dispatcherProvider = mockk(relaxed = true)

        // Mock DispatcherProvider setup for flows
        every { dispatcherProvider.io } returns kotlinx.coroutines.Dispatchers.Unconfined

        sharedPreferences = mockk(relaxed = true)
        sharedPreferencesEditor = mockk(relaxed = true)

        every { context.getSharedPreferences("survey_reminders", Context.MODE_PRIVATE) } returns sharedPreferences
        every { sharedPreferences.edit() } returns sharedPreferencesEditor
        every { sharedPreferencesEditor.putLong(any(), any()) } returns sharedPreferencesEditor
        every { sharedPreferencesEditor.putString(any(), any()) } returns sharedPreferencesEditor
        every { sharedPreferencesEditor.remove(any()) } returns sharedPreferencesEditor

        repository = SurveysRepositoryImpl(
            context,
            databaseService,
            UnconfinedTestDispatcher(),
            userSessionManager,
            sharedPrefManager,
            dispatcherProvider
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private inline fun <reified T : io.realm.RealmModel> mockQueryResults(vararg results: List<T>): RealmQuery<T> {
        val mockQuery = mockk<RealmQuery<T>>(relaxed = true)
        val mockResults = mockk<RealmResults<T>>(relaxed = true)

        every { mockRealm.where(T::class.java) } returns mockQuery

        // Setup fluent return
        every { mockQuery.equalTo(any<String>(), any<String>()) } returns mockQuery
        every { mockQuery.equalTo(any<String>(), any<Boolean>()) } returns mockQuery
        every { mockQuery.isNull(any<String>()) } returns mockQuery
        every { mockQuery.isNotNull(any<String>()) } returns mockQuery
        every { mockQuery.or() } returns mockQuery
        every { mockQuery.and() } returns mockQuery
        every { mockQuery.not() } returns mockQuery
        every { mockQuery.beginGroup() } returns mockQuery
        every { mockQuery.endGroup() } returns mockQuery
        every { mockQuery.`in`(any<String>(), any<Array<String>>()) } returns mockQuery

        every { mockQuery.findAll() } returns mockResults
        every { mockQuery.findFirst() } returns results.firstOrNull()?.firstOrNull()

        // Map sequential calls to copyFromRealm to different results
        if (results.size == 1) {
             every { mockRealm.copyFromRealm(mockResults) } returns results[0]
             every { mockRealm.copyFromRealm(any<T>()) } answers { firstArg() }
        } else if (results.isNotEmpty()) {
             every { mockRealm.copyFromRealm(mockResults) } returnsMany results.toList()
             every { mockRealm.copyFromRealm(any<T>()) } answers { firstArg() }
        }

        return mockQuery
    }

    @Test
    fun `getExamQuestions filters by examId`() = runTest {
        val mockResult = listOf(RealmExamQuestion().apply { examId = "exam1" })
        val mockQuery = mockQueryResults(mockResult)

        val result = repository.getExamQuestions("exam1")

        assertEquals(mockResult, result)
        verify {
            mockQuery.equalTo("examId", "exam1")
        }
    }

    @Test
    fun `getSurveys returns all surveys`() = runTest {
        val mockResult = listOf(RealmStepExam().apply { type = "surveys" })
        val mockQuery = mockQueryResults(mockResult)

        val result = repository.getSurveys()

        assertEquals(mockResult, result)
        verify {
            mockQuery.equalTo("type", "surveys")
        }
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
        verify { sharedPreferences.contains("reminder_time_survey1") }
    }

    @Test
    fun `isReminderScheduled returns false if not scheduled`() = runTest {
        every { sharedPreferences.contains("reminder_time_survey1") } returns false

        val result = repository.isReminderScheduled("survey1")

        assertFalse(result)
        verify { sharedPreferences.contains("reminder_time_survey1") }
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
    fun `adoptSurvey creates expected new Survey and Submission models`() = runTest {
        val user = RealmUser().apply { id = "user1" }
        coEvery { userSessionManager.getUserModel() } returns user
        every { sharedPrefManager.getParentCode() } returns "parentCode"
        every { sharedPrefManager.getPlanetCode() } returns "planetCode"

        val exam = RealmStepExam().apply {
            id = "exam1"
            name = "Test Exam"
            courseId = "course1"
        }
        val team = RealmMyTeam().apply {
            _id = "team1"
            name = "Test Team"
        }

        val newSurvey = RealmStepExam()
        val newSubmission = RealmSubmission()

        // Create explicit mock queries for exactly what is expected
        val examQuery1 = mockk<RealmQuery<RealmStepExam>>(relaxed = true)
        val examQuery2 = mockk<RealmQuery<RealmStepExam>>(relaxed = true)
        val examQuery3 = mockk<RealmQuery<RealmStepExam>>(relaxed = true)

        every { mockRealm.where(RealmStepExam::class.java) } returns examQuery1

        // Initial findFirst
        every { examQuery1.equalTo("id", "exam1") } returns examQuery2
        every { examQuery2.findFirst() } returns exam

        // Existing survey check
        every { examQuery1.equalTo("sourceSurveyId", "exam1") } returns examQuery3
        every { examQuery3.equalTo("teamId", "team1") } returns examQuery3
        every { examQuery3.findFirst() } returns null

        // For RealmMyTeam
        val teamQuery1 = mockk<RealmQuery<RealmMyTeam>>(relaxed = true)
        val teamQuery2 = mockk<RealmQuery<RealmMyTeam>>(relaxed = true)
        every { mockRealm.where(RealmMyTeam::class.java) } returns teamQuery1
        every { teamQuery1.equalTo("_id", "team1") } returns teamQuery2
        every { teamQuery2.findFirst() } returns team

        // For RealmSubmission
        val submissionQuery = mockk<RealmQuery<RealmSubmission>>(relaxed = true)
        every { mockRealm.where(RealmSubmission::class.java) } returns submissionQuery
        every { submissionQuery.equalTo(any<String>(), any<String>()) } returns submissionQuery
        every { submissionQuery.isNull(any<String>()) } returns submissionQuery
        every { submissionQuery.findFirst() } returns null

        val questionsQuery = mockk<RealmQuery<RealmExamQuestion>>(relaxed = true)
        every { mockRealm.where(RealmExamQuestion::class.java) } returns questionsQuery
        every { questionsQuery.equalTo("examId", "exam1") } returns questionsQuery
        every { questionsQuery.findAll() } returns mockk(relaxed = true)

        every { mockRealm.createObject(RealmStepExam::class.java, any<String>()) } returns newSurvey
        every { mockRealm.createObject(RealmSubmission::class.java, any<String>()) } returns newSubmission

        repository.adoptSurvey("exam1", "user1", "team1", true)

        assertEquals("Test Exam - Test Team", newSurvey.name)
        assertEquals("team1", newSurvey.teamId)
        assertEquals("exam1", newSurvey.sourceSurveyId)
        assertEquals(false, newSurvey.isTeamShareAllowed)

        assertEquals("exam1", newSubmission.parentId)
        assertEquals("user1", newSubmission.userId)
        assertEquals("survey", newSubmission.type)
        assertEquals("planetCode", newSubmission.source)
    }

    @Test
    fun `getSurveyInfos correctly maps submission counts`() = runTest {
        val survey1 = RealmStepExam().apply { id = "survey1"; createdDate = System.currentTimeMillis() }
        val submission1 = RealmSubmission().apply {
            parentId = "survey1"
            status = "complete"
            startTime = System.currentTimeMillis()
        }

        every { context.resources.getQuantityString(any(), any(), any()) } returns "1 submission"

        val mockSubmissionQuery = mockk<RealmQuery<RealmSubmission>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmSubmission>>(relaxed = true)
        every { mockRealm.where(RealmSubmission::class.java) } returns mockSubmissionQuery
        every { mockSubmissionQuery.equalTo(any<String>(), any<String>()) } returns mockSubmissionQuery
        every { mockSubmissionQuery.isNull(any<String>()) } returns mockSubmissionQuery
        every { mockSubmissionQuery.findAll() } returns mockResults

        val submissionsList = listOf(submission1)
        every { mockRealm.copyFromRealm(mockResults) } returns submissionsList

        val result = repository.getSurveyInfos(false, null, "user1", listOf(survey1))

        assertTrue(result.containsKey("survey1"))
        assertEquals("1 submission", result["survey1"]?.submissionCount)
    }

    @Test
    fun `getTeamOwnedSurveys returns expected exams based on team id and submissions`() = runTest {
        // Mock team submissions finding
        val submission = RealmSubmission().apply {
            parent = "{\"_id\": \"survey1\"}"
        }
        val mockSubmissionQuery = mockk<RealmQuery<RealmSubmission>>(relaxed = true)
        val mockSubmissionResults = mockk<RealmResults<RealmSubmission>>(relaxed = true)
        every { mockRealm.where(RealmSubmission::class.java) } returns mockSubmissionQuery
        every { mockSubmissionQuery.isNotNull("membershipDoc") } returns mockSubmissionQuery
        every { mockSubmissionQuery.equalTo("membershipDoc.teamId", "team1") } returns mockSubmissionQuery
        every { mockSubmissionQuery.findAll() } returns mockSubmissionResults
        every { mockRealm.copyFromRealm(mockSubmissionResults) } returns listOf(submission)

        // Mock step exams queries
        val exam = RealmStepExam().apply { id = "survey1"; type = "surveys"; teamId = "team1" }

        val mockExamQuery = mockk<RealmQuery<RealmStepExam>>(relaxed = true)
        val mockExamResults = mockk<RealmResults<RealmStepExam>>(relaxed = true)
        every { mockRealm.where(RealmStepExam::class.java) } returns mockExamQuery

        // This answers block isolates the two queries done in `getTeamOwnedSurveys`
        // 1st query: adopted survey IDs
        every { mockExamQuery.equalTo("teamId", "team1") } returns mockExamQuery
        every { mockExamQuery.isNotNull("sourceSurveyId") } returns mockExamQuery
        every { mockExamQuery.findAll() } returns mockk(relaxed = true)
        every { mockRealm.copyFromRealm(any<RealmResults<RealmStepExam>>()) } returns emptyList() // No adopted surveys for simplicity

        // 2nd query: final result
        every { mockExamQuery.equalTo("type", "surveys") } returns mockExamQuery
        every { mockExamQuery.beginGroup() } returns mockExamQuery
        every { mockExamQuery.or() } returns mockExamQuery
        every { mockExamQuery.`in`(any<String>(), any<Array<String>>()) } returns mockExamQuery
        every { mockExamQuery.endGroup() } returns mockExamQuery

        // Setup the specific results we want for the final return
        every { mockRealm.copyFromRealm(any<RealmResults<RealmStepExam>>()) } returnsMany listOf(
             emptyList(), // For adopted surveys
             listOf(exam) // For final result
        )

        val result = repository.getTeamOwnedSurveys("team1")
        assertEquals(1, result.size)
        assertEquals("survey1", result[0].id)
    }

    @Test
    fun `getAdoptableTeamSurveys returns expected exams`() = runTest {
        val exam = RealmStepExam().apply { id = "survey2"; type = "surveys"; isTeamShareAllowed = true }

        val mockSubmissionQuery = mockk<RealmQuery<RealmSubmission>>(relaxed = true)
        every { mockRealm.where(RealmSubmission::class.java) } returns mockSubmissionQuery
        every { mockSubmissionQuery.findAll() } returns mockk(relaxed = true)
        every { mockRealm.copyFromRealm(any<RealmResults<RealmSubmission>>()) } returns emptyList()

        val mockExamQuery = mockk<RealmQuery<RealmStepExam>>(relaxed = true)
        every { mockRealm.where(RealmStepExam::class.java) } returns mockExamQuery
        every { mockExamQuery.findAll() } returns mockk(relaxed = true)

        // Return no adopted surveys, then return exam2 for final query
        every { mockRealm.copyFromRealm(any<RealmResults<RealmStepExam>>()) } returnsMany listOf(
             emptyList(),
             listOf(exam)
        )

        val result = repository.getAdoptableTeamSurveys("team1")
        assertEquals(1, result.size)
        assertEquals("survey2", result[0].id)
        assertEquals(true, result[0].isTeamShareAllowed)
    }

    @Test
    fun `dueRemindersFlow emits valid reminders and removes them`() = runTest {
        val currentTime = System.currentTimeMillis()
        val pastTime = currentTime - 1000

        // Mock SharedPreferences
        every { sharedPreferences.all } returns mapOf(
            "reminder_time_survey1" to pastTime,
            "reminder_time_survey2" to currentTime + 10000 // Future time
        )
        every { sharedPreferences.getLong("reminder_time_survey1", 0) } returns pastTime
        every { sharedPreferences.getLong("reminder_time_survey2", 0) } returns currentTime + 10000

        val flow = repository.dueRemindersFlow()

        val result = flow.take(1).toList()

        assertEquals(1, result.size)
        assertEquals(listOf("survey1"), result[0])

        verify { sharedPreferencesEditor.remove("reminder_time_survey1") }
        verify { sharedPreferencesEditor.remove("reminder_surveys_survey1") }
    }
}
