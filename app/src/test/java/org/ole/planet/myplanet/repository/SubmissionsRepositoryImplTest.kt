package org.ole.planet.myplanet.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import javax.inject.Provider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.AnswerDao
import org.ole.planet.myplanet.data.room.dao.ExamDao
import org.ole.planet.myplanet.data.room.dao.QuestionDao
import org.ole.planet.myplanet.data.room.dao.SubmissionDao
import org.ole.planet.myplanet.data.room.dao.SubmitPhotosDao
import org.ole.planet.myplanet.model.CreateExamSubmissionRequest
import org.ole.planet.myplanet.model.ExamAnswerData
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.NetworkUtils

@ExperimentalCoroutinesApi
class SubmissionsRepositoryImplTest {

    private lateinit var teamsRepositoryProvider: Provider<TeamsRepository>
    private lateinit var surveysRepositoryProvider: Provider<SurveysRepository>
    private lateinit var context: Context
    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var exporter: SubmissionsRepositoryExporter

    private val submitPhotosDao: SubmitPhotosDao = mockk(relaxed = true)
    private val submissionDao: SubmissionDao = mockk(relaxed = true)
    private val answerDao: AnswerDao = mockk(relaxed = true)
    private val examDao: ExamDao = mockk(relaxed = true)
    private val questionDao: QuestionDao = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)
    private lateinit var repository: SubmissionsRepositoryImpl

    @Before
    fun setUp() {
        val teamsRepo = mockk<TeamsRepository>(relaxed = true)
        teamsRepositoryProvider = mockk(relaxed = true)
        every { teamsRepositoryProvider.get() } returns teamsRepo
        surveysRepositoryProvider = mockk(relaxed = true)
        context = mockk(relaxed = true)
        sharedPrefManager = mockk(relaxed = true)
        exporter = mockk(relaxed = true)

        repository = spyk(SubmissionsRepositoryImpl(
            teamsRepositoryProvider,
            context,
            sharedPrefManager,
            exporter,
            submitPhotosDao,
            submissionDao,
            answerDao,
            examDao,
            questionDao,
            Gson()
        ), recordPrivateCalls = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getPendingSurveysFlow queries correctly`() = runTest {
        every { submissionDao.observePendingSurveys("user_123") } returns kotlinx.coroutines.flow.flowOf(listOf(Submission(id = "submission1")))

        val result = repository.getPendingSurveysFlow("user_123").first()
        assertEquals(1, result.size)
    }

    @Test
    fun `getPendingSurveysFlow handles null userId`() = runTest {
        every { submissionDao.observePendingSurveys(null) } returns kotlinx.coroutines.flow.flowOf(emptyList())

        val result = repository.getPendingSurveysFlow(null).first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getSubmissionsFlow queries correctly`() = runTest {
        every { submissionDao.observeByUserId("user_123") } returns kotlinx.coroutines.flow.flowOf(listOf(Submission(id = "submission1")))

        val result = repository.getSubmissionsFlow("user_123").first()
        assertEquals(1, result.size)
    }

    @Test
    fun `getSubmissionsFlow suppresses equivalent emissions`() = runTest {
        val subList = listOf(Submission(id = "1", lastUpdateTime = 100L))
        val subListDup = listOf(Submission(id = "1", lastUpdateTime = 100L))

        val flowEmitter = kotlinx.coroutines.flow.MutableSharedFlow<List<Submission>>(replay = 1)
        every { submissionDao.observeByUserId("user_123") } returns flowEmitter

        var emissions = 0
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined).launch {
            repository.getSubmissionsFlow("user_123").collect {
                emissions++
            }
        }

        flowEmitter.emit(subList)
        assertEquals(1, emissions)

        // Equivalent list should be suppressed
        flowEmitter.emit(subListDup)
        assertEquals(1, emissions)

        job.cancel()
    }

    @Test
    fun `getSubmissionsFlow does not suppress when size changes`() = runTest {
        val subList = listOf(Submission(id = "1", lastUpdateTime = 100L))
        val subListDiffSize = listOf(Submission(id = "1", lastUpdateTime = 100L), Submission(id = "2", lastUpdateTime = 100L))

        val flowEmitter = kotlinx.coroutines.flow.MutableSharedFlow<List<Submission>>(replay = 1)
        every { submissionDao.observeByUserId("user_123") } returns flowEmitter

        var emissions = 0
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined).launch {
            repository.getSubmissionsFlow("user_123").collect {
                emissions++
            }
        }

        flowEmitter.emit(subList)
        assertEquals(1, emissions)

        // Different size list should not be suppressed
        flowEmitter.emit(subListDiffSize)
        assertEquals(2, emissions)

        job.cancel()
    }

    @Test
    fun `getSubmissionsFlow does not suppress when lastUpdateTime changes`() = runTest {
        val subList = listOf(Submission(id = "1", lastUpdateTime = 100L))
        val subListDiffTime = listOf(Submission(id = "1", lastUpdateTime = 101L))

        val flowEmitter = kotlinx.coroutines.flow.MutableSharedFlow<List<Submission>>(replay = 1)
        every { submissionDao.observeByUserId("user_123") } returns flowEmitter

        var emissions = 0
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined).launch {
            repository.getSubmissionsFlow("user_123").collect {
                emissions++
            }
        }

        flowEmitter.emit(subList)
        assertEquals(1, emissions)

        // Same size but different lastUpdateTime should not be suppressed
        flowEmitter.emit(subListDiffTime)
        assertEquals(2, emissions)

        job.cancel()
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
        coEvery { submissionDao.getUniquePendingSurveyCandidates("user") } returns listOf(
            Submission(id = "sub1", parentId = "exam1@course1"),
            Submission(id = "sub2", parentId = "exam2@course1"),
        )
        coEvery { answerDao.getBySubmissionIds(listOf("sub1", "sub2")) } returns emptyList()
        coEvery { examDao.getByIds(listOf("exam1", "exam2")) } returns emptyList()

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
        coEvery { submissionDao.getByUserId("test") } returns listOf(Submission(id = "submission1", userId = "test"))
        coEvery { answerDao.getBySubmissionIds(listOf("submission1")) } returns emptyList()

        val result = repository.getSubmissionsByUserId("test")
        assertEquals(1, result.size)
    }

    @Test
    fun `createBulkSurveySubmissions with empty list does not query or insert`() = runTest {
        val examId = "examId"
        coEvery { examDao.getById(examId) } returns StepExam(id = examId, courseId = "courseId")

        repository.createBulkSurveySubmissions(examId, emptyList())

        coVerify(exactly = 0) { submissionDao.getPendingByUsersAndParent(any(), any()) }
        coVerify(exactly = 0) { submissionDao.upsertAll(any()) }
    }

    @Test
    fun `createBulkSurveySubmissions with all new users bulk inserts all`() = runTest {
        val examId = "examId"
        val userIds = listOf("user1", "user2")
        val parentId = "examId@courseId"
        coEvery { examDao.getById(examId) } returns StepExam(id = examId, courseId = "courseId")
        coEvery { submissionDao.getPendingByUsersAndParent(userIds, parentId) } returns emptyList()

        repository.createBulkSurveySubmissions(examId, userIds)

        coVerify(exactly = 1) { submissionDao.getPendingByUsersAndParent(userIds, parentId) }
        coVerify(exactly = 1) {
            submissionDao.upsertAll(match {
                it.size == 2 &&
                it.map { sub -> sub.userId }.containsAll(userIds) &&
                it.all { sub -> sub.parentId == parentId && sub.status == "pending" && sub.type == "survey" }
            })
        }
    }

    @Test
    fun `createBulkSurveySubmissions with mixed users only inserts new users`() = runTest {
        val examId = "examId"
        val userIds = listOf("user1", "user2", "user3")
        val parentId = "examId@courseId"
        coEvery { examDao.getById(examId) } returns StepExam(id = examId, courseId = "courseId")
        val existingSubmission = Submission().apply { userId = "user2"; this.parentId = parentId; status = "pending" }
        coEvery { submissionDao.getPendingByUsersAndParent(userIds, parentId) } returns listOf(existingSubmission)

        repository.createBulkSurveySubmissions(examId, userIds)

        coVerify(exactly = 1) { submissionDao.getPendingByUsersAndParent(userIds, parentId) }
        coVerify(exactly = 1) {
            submissionDao.upsertAll(match {
                it.size == 2 &&
                it.map { sub -> sub.userId }.containsAll(listOf("user1", "user3"))
            })
        }
    }

    @Test
    fun `createBulkSurveySubmissions with all existing users does not insert`() = runTest {
        val examId = "examId"
        val userIds = listOf("user1", "user2")
        val parentId = "examId@courseId"
        coEvery { examDao.getById(examId) } returns StepExam(id = examId, courseId = "courseId")
        val existing1 = Submission().apply { userId = "user1"; this.parentId = parentId; status = "pending" }
        val existing2 = Submission().apply { userId = "user2"; this.parentId = parentId; status = "pending" }
        coEvery { submissionDao.getPendingByUsersAndParent(userIds, parentId) } returns listOf(existing1, existing2)

        repository.createBulkSurveySubmissions(examId, userIds)

        coVerify(exactly = 1) { submissionDao.getPendingByUsersAndParent(userIds, parentId) }
        coVerify(exactly = 0) { submissionDao.upsertAll(any()) }
    }

    @Test
    fun `saveSubmission upserts submission through Room`() = runTest {
        val sub = Submission().apply { id = "submission1" }

        repository.saveSubmission(sub)

        coVerify { submissionDao.upsertAll(match { it.single().id == "submission1" }) }
    }

    @Test
    fun `bulkInsertFromSync processes array correctly`() = runTest {
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

        repository.bulkInsertFromSync(jsonArray)

        verify { submissionDao.upsertAllBlocking(match { it.single().id == "test_id" }) }
    }

    @Test
    fun `bulkInsertFromSync stores JsonObject answer value as its json string`() = runTest {
        val answers = JsonArray().apply {
            add(JsonObject().apply {
                add("value", JsonObject().apply { addProperty("text", "nested") })
                addProperty("questionId", "q1")
            })
        }
        val jsonArray = JsonArray().apply {
            add(JsonObject().apply {
                add("doc", JsonObject().apply {
                    addProperty("_id", "sub_object_answer")
                    add("answers", answers)
                })
            })
        }

        // Regression: previously getAsString() on a JsonObject value threw
        // UnsupportedOperationException and failed the entire submissions sync.
        repository.bulkInsertFromSync(jsonArray)

        verify {
            answerDao.upsertAllBlocking(
                match { list -> list.single().value == "{\"text\":\"nested\"}" }
            )
        }
    }

    @Test
    fun `bulkInsertFromSync stores array answer value as valueChoices not value`() = runTest {
        val answers = JsonArray().apply {
            add(JsonObject().apply {
                add("value", JsonArray().apply { add("a"); add("b") })
                addProperty("questionId", "q1")
            })
        }
        val jsonArray = JsonArray().apply {
            add(JsonObject().apply {
                add("doc", JsonObject().apply {
                    addProperty("_id", "sub_array_answer")
                    add("answers", answers)
                })
            })
        }

        repository.bulkInsertFromSync(jsonArray)

        verify {
            answerDao.upsertAllBlocking(
                match { list ->
                    val answer = list.single()
                    answer.value == null && answer.valueChoices?.size == 2
                }
            )
        }
    }

    @Test
    fun `insertSubmission skips if _attachments present`() = runTest {
        val submission = JsonObject().apply { addProperty("_attachments", "test") }
        repository.insertSubmission(submission)
        verify(exactly = 0) { submissionDao.upsertAllBlocking(any()) }
    }

    @Test
    fun `insertSubmission upserts synced submission through Room`() = runTest {
        val submission = JsonObject().apply {
            addProperty("_id", "test_id")
            addProperty("status", "pending")
        }

        repository.insertSubmission(submission)

        verify { submissionDao.upsertAllBlocking(match { it.single().id == "test_id" }) }
    }

    @Test
    fun `deleteExamSubmissions deletes answers and submissions through Room`() = runTest {
        coEvery { submissionDao.getByParentUserAndStatus("exam@course", "user", null) } returns listOf(Submission(id = "submission1"))

        repository.deleteExamSubmissions("exam", "course", "user")

        coVerify { answerDao.deleteBySubmissionIds(listOf("submission1")) }
        coVerify { submissionDao.deleteByParentAndUser("exam@course", "user") }
    }

    @Test
    fun `hasSubmission returns true when match found`() = runTest {
        coEvery { questionDao.countByExamId("stepExamId") } returns 1

        coEvery { submissionDao.countByUserParentAndType("userId", "stepExamId@courseId", "type") } returns 1

        val result = repository.hasSubmission("stepExamId", "courseId", "userId", "type")
        assertTrue(result)
    }

    @Test
    fun `createExamSubmission creates and returns new submission`() = runTest {
        val exam = StepExam().apply {
            id = "exam_id"
            courseId = "course_id"
        }

        val result = repository.createExamSubmission(
            CreateExamSubmissionRequest("user", "dob", "gender", exam, "type", null)
        )

        assertEquals("exam_id@course_id", result?.parentId)
        coVerify { submissionDao.upsertAll(match { it.single().parentId == "exam_id@course_id" }) }
    }

    @Test
    fun `saveExamAnswer upserts answer through Room`() = runTest {
        val answerData = mockk<ExamAnswerData>(relaxed = true)
        val question = ExamQuestion().apply { id = "question1"; examId = "exam1"; type = "text" }
        val submission = Submission().apply { id = "submission1"; userId = "user1"; parentId = "exam1@course1" }

        every { answerData.component1() } returns submission
        every { answerData.component2() } returns question
        every { answerData.component3() } returns "answer text"
        every { answerData.component4() } returns null
        every { answerData.component5() } returns null
        every { answerData.component6() } returns false
        every { answerData.component7() } returns "survey"
        every { answerData.component8() } returns 0
        every { answerData.component9() } returns 1
        every { answerData.component10() } returns true
        coEvery { submissionDao.getByIdOrRemoteId("submission1") } returns Submission(id = "submission1", parentId = "exam1@course1", userId = "user1")

        val result = repository.saveExamAnswer(answerData)

        assertTrue(result)
        coVerify { answerDao.upsertAll(match { it.single().submissionId == "submission1" && it.single().value == "answer text" }) }
        coVerify { submissionDao.updateStatusAndLastUpdate("submission1", "complete", any()) }
    }

    @Test
    fun `serializeSubmission builds json output correctly and uses stored user blob`() = runTest {
        mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "androidId"
        every { NetworkUtils.getDeviceName() } returns "device"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "custom"

        val submission = Submission().apply {
            id = "s1"; userId = "u1"; parentId = "exam1@course1"; type = "survey"
            user = "{\"_id\":\"stored_user\"}"
        }

        val result = repository.serializeSubmission(submission, "planet", "parent")

        assertEquals("stored_user", result.getAsJsonObject("user").get("_id").asString)
    }

    @Test
    fun `markSubmissionComplete updates submission through Room`() = runTest {
        val payload = JsonObject().apply { addProperty("name", "Learner") }

        repository.markSubmissionComplete("test_id", payload)

        coVerify { submissionDao.markComplete("test_id", payload.toString()) }
    }

    @Test
    fun `getNormalizedSubmitterName returns name when valid json is provided`() {
        val submission = Submission().apply {
            user = "{\"name\": \"John Doe\", \"other\": \"value\"}"
        }
        val result = repository.getNormalizedSubmitterName(submission)
        assertEquals("John Doe", result)
    }

    @Test
    fun `getNormalizedSubmitterName returns null when name is blank`() {
        val submission = Submission().apply {
            user = "{\"name\": \"   \", \"other\": \"value\"}"
        }
        val result = repository.getNormalizedSubmitterName(submission)
        assertNull(result)
    }

    @Test
    fun `getNormalizedSubmitterName returns null when name key is missing`() {
        val submission = Submission().apply {
            user = "{\"other\": \"value\"}"
        }
        val result = repository.getNormalizedSubmitterName(submission)
        assertNull(result)
    }

    @Test
    fun `getNormalizedSubmitterName returns null when user is malformed json`() {
        val submission = Submission().apply {
            user = "invalid json"
        }
        val result = repository.getNormalizedSubmitterName(submission)
        assertNull(result)
    }

    @Test
    fun `getNormalizedSubmitterName returns null when user is null`() {
        val submission = Submission().apply {
            user = null
        }
        val result = repository.getNormalizedSubmitterName(submission)
        assertNull(result)
    }

    @Test
    fun `getNormalizedSubmitterName returns null when user is blank`() {
        val submission = Submission().apply {
            user = "   "
        }
        val result = repository.getNormalizedSubmitterName(submission)
        assertNull(result)
    }
}
