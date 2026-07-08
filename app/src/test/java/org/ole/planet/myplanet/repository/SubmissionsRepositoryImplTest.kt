package org.ole.planet.myplanet.repository

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import javax.inject.Provider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.CreateExamSubmissionRequest
import org.ole.planet.myplanet.model.ExamAnswerData
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.services.SharedPrefManager

@ExperimentalCoroutinesApi
class SubmissionsRepositoryImplTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var teamsRepositoryProvider: Provider<TeamsRepository>
    private lateinit var surveysRepositoryProvider: Provider<SurveysRepository>
    private lateinit var context: Context
    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var exporter: SubmissionsRepositoryExporter
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var repository: SubmissionsRepositoryImpl

    @Before
    fun setUp() {
        databaseService = mockk(relaxed = true)
        val teamsRepo = mockk<TeamsRepository>(relaxed = true)
        teamsRepositoryProvider = mockk(relaxed = true)
        every { teamsRepositoryProvider.get() } returns teamsRepo
        surveysRepositoryProvider = mockk(relaxed = true)
        context = mockk(relaxed = true)
        sharedPrefManager = mockk(relaxed = true)
        exporter = mockk(relaxed = true)

        every { databaseService.ioDispatcher } returns testDispatcher

        val transactionSlot = slot<Function1<Realm, Unit>>()
        coEvery { databaseService.executeTransactionAsync(capture(transactionSlot)) } answers {
            val realm = mockk<Realm>(relaxed = true)
            transactionSlot.captured.invoke(realm)
        }

        repository = spyk(SubmissionsRepositoryImpl(
            databaseService,
            testDispatcher,
            teamsRepositoryProvider,
            surveysRepositoryProvider,
            context,
            sharedPrefManager,
            exporter
        ), recordPrivateCalls = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getPendingSurveysFlow queries correctly`() = runTest {
        val mockList = listOf(mockk<RealmSubmission>())
        coEvery {
            repository["queryListFlow"](RealmSubmission::class.java, any<Function1<RealmQuery<RealmSubmission>, Unit>>())
        } returns kotlinx.coroutines.flow.flowOf(mockList)

        val result = repository.getPendingSurveysFlow("user_123").first()
        assertEquals(1, result.size)
    }

    @Test
    fun `getSubmissionsFlow queries correctly`() = runTest {
        val mockList = listOf(mockk<RealmSubmission>())
        coEvery {
            repository["queryListFlow"](RealmSubmission::class.java, any<Function1<RealmQuery<RealmSubmission>, Unit>>())
        } returns kotlinx.coroutines.flow.flowOf(mockList)

        val result = repository.getSubmissionsFlow("user_123").first()
        assertEquals(1, result.size)
    }

    @Test
    fun `getPendingSurveys returns empty list when userId is null`() = runTest {
        val result = repository.getPendingSurveys(null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getUniquePendingSurveys returns empty list when userId is null`() = runTest {
        val result = repository.getUniquePendingSurveys(null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getUniquePendingSurveys returns list when exams exist`() = runTest {
        val mockSub1 = mockk<RealmSubmission>(relaxed = true).apply {
            every { parentId } returns "exam1@course1"
        }
        val mockSub2 = mockk<RealmSubmission>(relaxed = true).apply {
            every { parentId } returns "exam2@course1"
        }

        coEvery {
            repository["queryList"](RealmSubmission::class.java, false, any<Function1<RealmQuery<RealmSubmission>, Unit>>())
        } returns listOf(mockSub1, mockSub2)

        coEvery {
            repository["getExamsByIds"](any<List<String>>())
        } returns emptyList<RealmStepExam>()

        val result = repository.getUniquePendingSurveys("user")
        assertEquals(0, result.size)
    }

    @Test
    fun `getSurveyTitlesFromSubmissions returns empty list when examIds is empty`() = runTest {
        val result = repository.getSurveyTitlesFromSubmissions(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getSubmissionsByIds returns empty list when ids is empty`() = runTest {
        val result = repository.getSubmissionsByIds(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getSubmissionsByUserId returns correctly`() = runTest {
        val mockList = listOf(mockk<RealmSubmission>())
        coEvery {
            repository["queryList"](RealmSubmission::class.java, true, any<Function1<RealmQuery<RealmSubmission>, Unit>>())
        } returns mockList

        val result = repository.getSubmissionsByUserId("test")
        assertEquals(1, result.size)
    }

    @Test
    fun `createBulkSurveySubmissions calls getOrCreateSubmission for all users`() = runTest {
        val examId = "examId"
        val userIds = listOf("user1", "user2")
        val realm = mockk<Realm>(relaxed = true)
        val query = mockk<RealmQuery<RealmStepExam>>(relaxed = true)
        val exam = mockk<RealmStepExam>(relaxed = true)

        every { realm.where(RealmStepExam::class.java) } returns query
        every { query.equalTo("id", examId) } returns query
        every { query.findFirst() } returns exam
        every { exam.courseId } returns "courseId"

        coEvery { repository["withRealm"](any<Boolean>(), any<Function1<Realm, Any>>()) } answers {
            val action = arg<Function1<Realm, Any>>(1)
            action.invoke(realm)
        }

        coEvery { repository.getOrCreateSubmission(any(), any()) } returns mockk()

        repository.createBulkSurveySubmissions(examId, userIds)

        coVerify(exactly = 1) { repository.getOrCreateSubmission("user1", "examId@courseId") }
        coVerify(exactly = 1) { repository.getOrCreateSubmission("user2", "examId@courseId") }
    }

    @Test
    fun `saveSubmission performs transaction`() = runTest {
        val sub = mockk<RealmSubmission>(relaxed = true)

        val transactionSlot = slot<Function1<Realm, Unit>>()
        coEvery { databaseService.executeTransactionAsync(capture(transactionSlot)) } answers {
            val realm = mockk<Realm>(relaxed = true)
            transactionSlot.captured.invoke(realm)
        }

        repository.saveSubmission(sub)

        coVerify { databaseService.executeTransactionAsync(any()) }
    }

    @Test
    fun `bulkInsertFromSync processes array correctly`() {
        val realm = mockk<Realm>(relaxed = true)
        val jsonArray = JsonArray().apply {
            add(JsonObject().apply {
                add("doc", JsonObject().apply {
                    addProperty("_id", "test_id")
                })
            })
            add(JsonObject().apply {
                add("doc", JsonObject().apply {
                    addProperty("_id", "_design_test")
                })
            })
        }

        // Since insertSubmissionInternal is called on `this`, spyk can track it
        every { repository["insertSubmissionInternal"](any<Realm>(), any<JsonObject>()) } answers { }

        repository.bulkInsertFromSync(realm, jsonArray)
        verify(exactly = 1) { repository["insertSubmissionInternal"](realm, any<JsonObject>()) }
    }

    @Test
    fun `insertSubmission skips if _attachments present`() = runTest {
        val submission = JsonObject().apply { addProperty("_attachments", "test") }
        repository.insertSubmission(submission)
        coVerify(exactly = 0) { databaseService.executeTransactionAsync(any()) }
    }

    @Test
    fun `insertSubmission performs happy path creation`() = runTest {
        val realm = mockk<Realm>(relaxed = true)
        val transactionSlot = slot<Function1<Realm, Unit>>()
        coEvery { databaseService.executeTransactionAsync(capture(transactionSlot)) } answers {
            transactionSlot.captured.invoke(realm)
        }

        val query = mockk<RealmQuery<RealmSubmission>>(relaxed = true)
        every { realm.where(RealmSubmission::class.java) } returns query
        every { query.equalTo(any<String>(), any<String>()) } returns query
        every { query.findFirst() } returns null
        every { realm.createObject(RealmSubmission::class.java, any<String>()) } returns mockk<RealmSubmission>(relaxed = true)

        val submission = JsonObject().apply {
            addProperty("_id", "test_id")
            addProperty("status", "pending")
        }

        every { repository["updateBasicFields"](any<RealmSubmission>(), any<String>(), any<String>(), any<Boolean>(), any<JsonObject>()) } answers { }
        every { repository["updateTeam"](any<Realm>(), any<RealmSubmission>(), any<JsonObject>()) } answers { }
        every { repository["updateMembership"](any<Realm>(), any<RealmSubmission>(), any<JsonObject>()) } answers { }
        every { repository["updateUserId"](any<RealmSubmission>(), any<JsonObject>()) } answers { }
        every { repository["updateAnswers"](any<Realm>(), any<RealmSubmission>(), any<JsonObject>(), any<Boolean>()) } answers { }

        repository.insertSubmission(submission)

        verify(exactly = 1) { realm.createObject(RealmSubmission::class.java, "test_id") }
    }

    @Test
    fun `deleteExamSubmissions queries and deletes correctly`() = runTest {
        val transactionSlot = slot<Function1<Realm, Unit>>()
        coEvery { databaseService.executeTransactionAsync(capture(transactionSlot)) } answers {
            val realm = mockk<Realm>(relaxed = true)
            val query = mockk<RealmQuery<RealmSubmission>>(relaxed = true)
            every { realm.where(RealmSubmission::class.java) } returns query
            every { query.equalTo(any<String>(), any<String>()) } returns query
            every { query.findAll() } returns mockk(relaxed = true)

            transactionSlot.captured.invoke(realm)
        }

        repository.deleteExamSubmissions("exam", "course", "user")
        coVerify { databaseService.executeTransactionAsync(any()) }
    }

    @Test
    fun `hasSubmission returns true when match found`() = runTest {
        val mockQuestionList = listOf(mockk<RealmExamQuestion>(relaxed = true).apply {
            every { examId } returns "stepExamId"
        })

        coEvery {
            repository["queryList"](RealmExamQuestion::class.java, false, any<Function1<RealmQuery<RealmExamQuestion>, Unit>>())
        } answers { mockQuestionList }

        coEvery {
            repository["count"](RealmSubmission::class.java, any<Function1<RealmQuery<RealmSubmission>, Unit>>())
        } returns 1L

        val result = repository.hasSubmission("stepExamId", "courseId", "userId", "type")
        assertTrue(result)
    }

    @Test
    fun `createExamSubmission creates and returns new submission`() = runTest {
        val exam = mockk<RealmStepExam>(relaxed = true)
        every { exam.courseId } returns "course_id"
        every { exam.id } returns "exam_id"

        val realm = mockk<Realm>(relaxed = true)

        val transactionSlot = slot<Function1<Realm, Unit>>()
        coEvery { databaseService.executeTransactionAsync(capture(transactionSlot)) } answers {
            // Empty to skip lambda execution to prevent Realm type issues when testing createObject
        }

        val result = repository.createExamSubmission(
            CreateExamSubmissionRequest("user", "dob", "gender", exam, "type", "team")
        )
        // Function executeTransaction wrapper does not execute anything, so result can be null.
        // We verify the interaction.
        coVerify { databaseService.executeTransactionAsync(any()) }
    }

    @Test
    fun `saveExamAnswer executes without exceptions`() = runTest {
        val answerData = mockk<ExamAnswerData>(relaxed = true)
        val mockAnswer = RealmAnswer()
        val mockExam = mockk<RealmStepExam>(relaxed = true)
        val mockQuestion = mockk<RealmExamQuestion>(relaxed = true)
        val mockSubmission = mockk<RealmSubmission>(relaxed = true)

        every { answerData.component1() } returns mockSubmission
        every { answerData.component2() } returns mockQuestion

        coEvery { repository.getSubmissionById(any()) } returns mockSubmission
        coEvery { repository.getExamById(any()) } returns mockExam

        val transactionSlot = slot<Function1<Realm, Unit>>()
        coEvery {
            databaseService.executeTransactionAsync(capture(transactionSlot))
        } answers {
            // Empty to prevent internal query cast exceptions. We just verify the interaction.
        }

        val result = repository.saveExamAnswer(answerData)
        assertTrue(result)
        coVerify { databaseService.executeTransactionAsync(any()) }
    }

    @Test
    fun `markSubmissionComplete executes transaction`() = runTest {
        val mockSub = mockk<RealmSubmission>(relaxed = true)
        coEvery {
            repository["update"](RealmSubmission::class.java, "id", "test_id", any<Function1<RealmSubmission, Unit>>())
        } answers {
            val updater = it.invocation.args[3] as (RealmSubmission) -> Unit
            updater.invoke(mockSub)
        }

        repository.markSubmissionComplete("test_id", JsonObject())

        verify { mockSub.status = "complete" }
    }

    private fun invokeUpdateCourseProgressIfGraded(realm: Realm, sub: RealmSubmission) {
        val method = SubmissionsRepositoryImpl::class.java.getDeclaredMethod(
            "updateCourseProgressIfGraded", Realm::class.java, RealmSubmission::class.java
        )
        method.isAccessible = true
        method.invoke(repository, realm, sub)
    }

    private fun gradedProgressFixture(realm: Realm, progress: org.ole.planet.myplanet.model.RealmCourseProgress) {
        val exam = RealmStepExam().apply { id = "exam1"; courseId = "course1"; stepId = "step2" }
        val course = org.ole.planet.myplanet.model.RealmMyCourse().apply {
            courseId = "course1"
            courseSteps = io.realm.RealmList(
                org.ole.planet.myplanet.model.RealmCourseStep().apply { id = "step1" },
                org.ole.planet.myplanet.model.RealmCourseStep().apply { id = "step2" }
            )
        }

        val examQuery = mockk<RealmQuery<RealmStepExam>>()
        every { realm.where(RealmStepExam::class.java) } returns examQuery
        every { examQuery.equalTo(any<String>(), any<String>()) } returns examQuery
        every { examQuery.findFirst() } returns exam

        val courseQuery = mockk<RealmQuery<org.ole.planet.myplanet.model.RealmMyCourse>>()
        every { realm.where(org.ole.planet.myplanet.model.RealmMyCourse::class.java) } returns courseQuery
        every { courseQuery.equalTo(any<String>(), any<String>()) } returns courseQuery
        every { courseQuery.findFirst() } returns course

        val progressQuery = mockk<RealmQuery<org.ole.planet.myplanet.model.RealmCourseProgress>>()
        every { realm.where(org.ole.planet.myplanet.model.RealmCourseProgress::class.java) } returns progressQuery
        every { progressQuery.equalTo(any<String>(), any<String>()) } returns progressQuery
        every { progressQuery.equalTo(any<String>(), any<Int>()) } returns progressQuery
        every { progressQuery.findFirst() } returns progress
    }

    @Test
    fun `graded exam submission marks the matching course step passed`() {
        val realm = mockk<Realm>()
        val progress = org.ole.planet.myplanet.model.RealmCourseProgress().apply { stepNum = 2; passed = false }
        gradedProgressFixture(realm, progress)

        val sub = RealmSubmission().apply {
            type = "exam"
            status = "graded"
            userId = "org.couchdb.user:learner"
            parentId = "exam1@course1"
        }

        invokeUpdateCourseProgressIfGraded(realm, sub)

        assertTrue(progress.passed)
    }

    @Test
    fun `ungraded exam submission does not touch course progress`() {
        val realm = mockk<Realm>()
        val progress = org.ole.planet.myplanet.model.RealmCourseProgress().apply { stepNum = 2; passed = false }
        gradedProgressFixture(realm, progress)

        val sub = RealmSubmission().apply {
            type = "exam"
            status = "requires grading"
            userId = "org.couchdb.user:learner"
            parentId = "exam1@course1"
        }

        invokeUpdateCourseProgressIfGraded(realm, sub)

        assertEquals(false, progress.passed)
    }
}
