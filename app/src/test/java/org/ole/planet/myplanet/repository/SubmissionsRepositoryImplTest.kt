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
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import javax.inject.Provider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.AnswerDao
import org.ole.planet.myplanet.data.room.dao.ExamDao
import org.ole.planet.myplanet.data.room.dao.QuestionDao
import org.ole.planet.myplanet.data.room.dao.SubmissionDao
import org.ole.planet.myplanet.data.room.dao.SubmitPhotosDao
import org.ole.planet.myplanet.data.room.dao.SubmitPhotosDao.UploadedPhoto
import org.ole.planet.myplanet.model.CreateExamSubmissionRequest
import org.ole.planet.myplanet.model.ExamAnswerData
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.model.TeamReference
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

    private suspend fun TestScope.countEmissionsFor(
        first: List<Submission>,
        second: List<Submission>,
    ): Int {
        val flowEmitter = MutableSharedFlow<List<Submission>>(replay = 1)
        every { submissionDao.observeByUserId("user_123") } returns flowEmitter

        var emissions = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.getSubmissionsFlow("user_123").collect { emissions++ }
        }

        flowEmitter.emit(first)
        assertEquals("the first emission always reaches the collector", 1, emissions)
        flowEmitter.emit(second)
        return emissions
    }

    @Test
    fun `getSubmissionsFlow suppresses equivalent emissions`() = runTest {
        val emissions = countEmissionsFor(
            listOf(Submission(id = "1", lastUpdateTime = 100L)),
            listOf(Submission(id = "1", lastUpdateTime = 100L)),
        )
        // equivalent list is suppressed
        assertEquals(1, emissions)
    }

    @Test
    fun `getSubmissionsFlow does not suppress when size changes`() = runTest {
        val emissions = countEmissionsFor(
            listOf(Submission(id = "1", lastUpdateTime = 100L)),
            listOf(Submission(id = "1", lastUpdateTime = 100L), Submission(id = "2", lastUpdateTime = 100L)),
        )
        // different size is not suppressed
        assertEquals(2, emissions)
    }

    @Test
    fun `getSubmissionsFlow does not suppress when lastUpdateTime changes`() = runTest {
        val emissions = countEmissionsFor(
            listOf(Submission(id = "1", lastUpdateTime = 100L)),
            listOf(Submission(id = "1", lastUpdateTime = 101L)),
        )
        // same size, different lastUpdateTime is not suppressed
        assertEquals(2, emissions)
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
    fun `startExamSession with deleteStale true deletes and creates new submission`() = runTest {
        val exam = StepExam().apply {
            id = "exam_id"
            courseId = "course_id"
        }
        val request = CreateExamSubmissionRequest("user", "dob", "gender", exam, "exam", null)

        coEvery { submissionDao.getByParentUserAndStatus(any(), any(), any()) } returns emptyList()
        coEvery { answerDao.deleteBySubmissionIds(any()) } returns 1

        val result = repository.startExamSession("exam_id", "parentId", "user", request, recreate = true, deleteStale = true)

        assertEquals("exam_id@course_id", result.parentId)
        coVerify { submissionDao.getByParentUserAndStatus("exam_id@course_id", "user", null) }
        coVerify { submissionDao.upsertAll(match { it.single().parentId == "exam_id@course_id" }) }
    }

    @Test
    fun `startExamSession with recreate true throws IllegalStateException on max retries`() = runTest {
        val exam = StepExam().apply {
            id = "exam_id"
            courseId = "course_id"
        }
        val request = CreateExamSubmissionRequest("user", "dob", "gender", exam, "exam", null)

        coEvery { submissionDao.upsertAll(any<List<Submission>>()) } throws RuntimeException("SQLite constraint")

        val exception = org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                repository.startExamSession("exam_id", "parentId", "user", request, recreate = true, deleteStale = true)
            }
        }
        assertTrue(exception.message?.contains("Failed to start exam session after 3 attempts") == true)

        coVerify(exactly = 3) { submissionDao.upsertAll(any<List<Submission>>()) }
    }

    @Test
    fun `startExamSession with recreate false returns pending if exists`() = runTest {
        val exam = StepExam().apply {
            id = "exam_id"
            courseId = "course_id"
        }
        val request = CreateExamSubmissionRequest("user", "dob", "gender", exam, "survey", null)
        val existingSubmission = Submission().apply { id = "existing_id" }

        coEvery { submissionDao.getByParentUserAndStatus("parentId", "user", "pending") } returns listOf(existingSubmission)

        val result = repository.startExamSession("exam_id", "parentId", "user", request, recreate = false)

        assertEquals("existing_id", result.id)
        coVerify(exactly = 0) { submissionDao.upsertAll(any<List<Submission>>()) }
    }

    @Test
    fun `startExamSession with recreate false creates new if no pending exists`() = runTest {
        val exam = StepExam().apply {
            id = "exam_id"
            courseId = "course_id"
        }
        val request = CreateExamSubmissionRequest("user", "dob", "gender", exam, "survey", null)

        coEvery { submissionDao.getByParentUserAndStatus("parentId", "user", "pending") } returns emptyList()
        coEvery { answerDao.deleteBySubmissionIds(any()) } returns 1

        val result = repository.startExamSession("exam_id", "parentId", "user", request, recreate = false)

        assertEquals("exam_id@course_id", result.parentId)
        coVerify { submissionDao.upsertAll(match { it.single().parentId == "exam_id@course_id" }) }
    }

    @Test
    fun `startExamSession with recreate true and deleteStale false creates without deleting`() = runTest {
        val exam = StepExam().apply {
            id = "exam_id"
            courseId = "course_id"
        }
        val request = CreateExamSubmissionRequest("user", "dob", "gender", exam, "survey", "team_id")

        coEvery { submissionDao.getByParentUserAndStatus(any(), any(), any()) } returns emptyList()

        val result = repository.startExamSession("exam_id", "parentId", "user", request, recreate = true, deleteStale = false)

        assertEquals("exam_id@course_id", result.parentId)
        coVerify(exactly = 0) { submissionDao.getByParentUserAndStatus("exam_id@course_id", "user", null) }
        coVerify { submissionDao.upsertAll(match { it.single().parentId == "exam_id@course_id" }) }
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
    fun `serializeSubmission uploads fresh user data instead of the stored blob`() = runTest {
        mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "androidId"
        every { NetworkUtils.getDeviceName() } returns "device"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "custom"

        // Fresh user record from Room (attachment-free, current) must win over the persisted
        // blob, whose _attachments were stripped for storage safety.
        val freshUser = mockk<UserEntity>()
        every { freshUser.serialize() } returns JsonObject().apply { addProperty("_id", "fresh_user") }

        val submission = Submission().apply {
            id = "s1"; userId = "u1"; parentId = "exam1@course1"; type = "survey"
            user = "{\"_id\":\"stored_user\"}"
        }

        val result = repository.serializeSubmission(submission, "planet", "parent", freshUser)

        assertEquals("fresh_user", result.getAsJsonObject("user").get("_id").asString)
    }

    @Test
    fun `serializeSubmission keeps account credential material out of the submission`() = runTest {
        mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "androidId"
        every { NetworkUtils.getDeviceName() } returns "device"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "custom"

        val account = mockk<UserEntity>()
        every { account.serialize() } returns JsonObject().apply {
            addProperty("_id", "org.couchdb.user:gg")
            addProperty("name", "gg")
            addProperty("derived_key", "6d250fd24fbc58596381c3a0b5e011e55b623cb7")
            addProperty("salt", "a2b98b9b1ec4ada569badd244ab7deee")
            addProperty("password_scheme", "pbkdf2")
            addProperty("iterations", 10)
            addProperty("password", "hunter2")
            add("roles", com.google.gson.JsonArray())
        }

        val submission = Submission().apply {
            id = "s1"; userId = "u1"; parentId = "exam1@course1"; type = "survey"
        }

        val result = repository.serializeSubmission(submission, "planet", "parent", account)

        val user = result.getAsJsonObject("user")
        assertEquals("org.couchdb.user:gg", user.get("_id").asString)
        assertEquals("gg", user.get("name").asString)
        listOf("derived_key", "salt", "password_scheme", "iterations", "password", "roles").forEach { key ->
            assertFalse("$key must never reach the submissions database", user.has(key))
        }
        assertFalse(result.toString().contains("derived_key"))
    }

    @Test
    fun `serializeSubmission attributes a collected response to respondent and collector`() = runTest {
        mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "androidId"
        every { NetworkUtils.getDeviceName() } returns "device"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "custom"

        val account = mockk<UserEntity>()
        every { account.serialize() } returns JsonObject().apply {
            addProperty("_id", "org.couchdb.user:gg")
            addProperty("name", "gg")
            addProperty("gender", "")
            addProperty("age", "")
            addProperty("derived_key", "6d250fd24fbc58596381c3a0b5e011e55b623cb7")
        }

        // A walk-up respondent surveyed by gg: their details are the stored blob, not gg's account.
        val submission = Submission().apply {
            id = "s1"; userId = "u1"; parentId = "survey1"; type = "survey"
            user = "{\"age\":\"34\",\"gender\":\"male\",\"membershipDoc\":{\"teamId\":\"team1\"}}"
        }

        val result = repository.serializeSubmission(submission, "planet", "parent", account)

        val respondent = result.getAsJsonObject("respondent")
        assertEquals("34", respondent.get("age").asString)
        assertEquals("male", respondent.get("gender").asString)

        val collectedBy = result.getAsJsonObject("collectedBy")
        assertEquals("org.couchdb.user:gg", collectedBy.get("_id").asString)
        assertEquals("gg", collectedBy.get("name").asString)

        // The collector is never the respondent, so the account must not surface as `user`.
        val user = result.getAsJsonObject("user")
        assertEquals("34", user.get("age").asString)
        assertEquals("male", user.get("gender").asString)
        assertFalse(user.has("_id"))
        assertFalse(user.has("name"))
        assertFalse(result.toString().contains("derived_key"))
    }

    @Test
    fun `serializeSubmission leaves a self-taken submission unattributed to a collector`() = runTest {
        mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "androidId"
        every { NetworkUtils.getDeviceName() } returns "device"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "custom"

        val account = mockk<UserEntity>()
        every { account.serialize() } returns JsonObject().apply {
            addProperty("_id", "org.couchdb.user:gg")
            addProperty("name", "gg")
        }

        val submission = Submission().apply {
            id = "s1"; userId = "u1"; parentId = "exam1@course1"; type = "exam"
        }

        val result = repository.serializeSubmission(submission, "planet", "parent", account)

        assertEquals("org.couchdb.user:gg", result.getAsJsonObject("user").get("_id").asString)
        assertFalse(result.has("respondent"))
        assertFalse(result.has("collectedBy"))
        assertEquals("myplanet", result.get("channel").asString)
    }

    @Test
    fun `serializeSubmission falls back to stored user blob when no fresh user exists`() = runTest {
        mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "androidId"
        every { NetworkUtils.getDeviceName() } returns "device"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "custom"

        val submission = Submission().apply {
            id = "s1"; userId = "u1"; parentId = "exam1@course1"; type = "survey"
            user = "{\"_id\":\"stored_user\"}"
        }

        val result = repository.serializeSubmission(submission, "planet", "parent", null)

        assertEquals("stored_user", result.getAsJsonObject("user").get("_id").asString)
    }

    @Test
    fun `createExamSubmission persists team id through Room when local team is missing`() = runTest {
        mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "androidId"
        every { NetworkUtils.getDeviceName() } returns "device"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "custom"
        coEvery { teamsRepositoryProvider.get().getTeamById("team1") } returns null

        val persistedSubmissions = slot<List<Submission>>()
        coEvery { submissionDao.upsertAll(capture(persistedSubmissions)) } returns Unit
        val exam = StepExam().apply {
            id = "exam_id"
            courseId = "course_id"
        }

        repository.createExamSubmission(
            CreateExamSubmissionRequest("user", "dob", "gender", exam, "survey", "team1")
        )

        val persisted = persistedSubmissions.captured.single()
        assertEquals("team1", persisted.teamId)

        // Reconstruct the entity as Room would: @Ignore fields are absent, while teamId survives.
        val reconstructed = Submission().apply {
            id = persisted.id
            userId = persisted.userId
            parentId = persisted.parentId
            type = persisted.type
            teamId = persisted.teamId
        }

        val result = repository.serializeSubmission(reconstructed, "planet", "parent", null)

        val team = result.getAsJsonObject("team")
        assertEquals("team1", team.get("_id").asString)
        assertNull(team.get("name"))
        assertNull(team.get("type"))
    }

    @Test
    fun `serializeSubmission emits persisted team id when local team is missing`() = runTest {
        mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "androidId"
        every { NetworkUtils.getDeviceName() } returns "device"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "custom"
        coEvery { teamsRepositoryProvider.get().getTeamById("team1") } returns null

        val submission = Submission().apply {
            id = "s1"; userId = "u1"; parentId = "exam1@course1"; type = "survey"
            teamId = "team1"
        }

        val result = repository.serializeSubmission(submission, "planet", "parent", null)

        val team = result.getAsJsonObject("team")
        assertEquals("team1", team.get("_id").asString)
        assertNull(team.get("name"))
        assertNull(team.get("type"))
    }

    @Test
    fun `serializeSubmission emits persisted team id when local team lookup throws`() = runTest {
        mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "androidId"
        every { NetworkUtils.getDeviceName() } returns "device"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "custom"
        coEvery { teamsRepositoryProvider.get().getTeamById("team1") } throws IllegalStateException("lookup failed")

        val submission = Submission().apply {
            id = "s1"; userId = "u1"; parentId = "exam1@course1"; type = "survey"
            teamId = "team1"
        }

        val result = repository.serializeSubmission(submission, "planet", "parent", null)

        val team = result.getAsJsonObject("team")
        assertEquals("team1", team.get("_id").asString)
        assertNull(team.get("name"))
        assertNull(team.get("type"))
    }

    @Test
    fun `serializeSubmission enriches persisted team id with actual local metadata`() = runTest {
        mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "androidId"
        every { NetworkUtils.getDeviceName() } returns "device"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "custom"

        coEvery { teamsRepositoryProvider.get().getTeamById("team1") } returns MyTeam().apply {
            _id = "team1"
            name = "Enterprise One"
            type = "enterprise"
        }

        val submission = Submission().apply {
            id = "s1"; userId = "u1"; parentId = "exam1@course1"; type = "survey"
            teamId = "team1"
        }

        val result = repository.serializeSubmission(submission, "planet", "parent", null)

        val team = result.getAsJsonObject("team")
        assertEquals("team1", team.get("_id").asString)
        assertEquals("Enterprise One", team.get("name").asString)
        assertEquals("enterprise", team.get("type").asString)
    }

    @Test
    fun `serializeSubmission uses in-memory team object`() = runTest {
        mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "androidId"
        every { NetworkUtils.getDeviceName() } returns "device"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "custom"

        val submission = Submission().apply {
            id = "s1"; userId = "u1"; parentId = "exam1@course1"; type = "survey"
            teamObject = TeamReference().apply {
                _id = "enterprise1"
                name = "Enterprise One"
                type = "enterprise"
            }
        }

        val result = repository.serializeSubmission(submission, "planet", "parent", null)

        val team = result.getAsJsonObject("team")
        assertEquals("enterprise1", team.get("_id").asString)
        assertEquals("Enterprise One", team.get("name").asString)
        assertEquals("enterprise", team.get("type").asString)
        coVerify(exactly = 0) { teamsRepositoryProvider.get().getTeamById(any()) }
    }

    @Test
    fun `serializeSubmission omits the team for a submission with no team`() = runTest {
        mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "androidId"
        every { NetworkUtils.getDeviceName() } returns "device"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "custom"

        val submission = Submission().apply {
            id = "s1"; userId = "u1"; parentId = "exam1@course1"; type = "survey"
        }

        val result = repository.serializeSubmission(submission, "planet", "parent", null)

        assertNull(result.get("team"))
    }

    @Test
    fun `getExamUploadPayload emits persisted team id when local team is missing`() = runTest {
        mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "androidId"
        every { NetworkUtils.getDeviceName() } returns "device"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "custom"
        coEvery { teamsRepositoryProvider.get().getTeamById("team1") } returns null

        val submission = Submission().apply {
            id = "s1"; userId = "u1"; parentId = "exam1@course1"; type = "exam"
            teamId = "team1"
        }

        val result = repository.getExamUploadPayload(submission, null)

        val team = result.getAsJsonObject("team")
        assertEquals("team1", team.get("_id").asString)
        assertNull(team.get("name"))
        assertNull(team.get("type"))
    }

    @Test
    fun `getExamUploadPayload emits persisted team id when local team lookup throws`() = runTest {
        mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "androidId"
        every { NetworkUtils.getDeviceName() } returns "device"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "custom"
        coEvery { teamsRepositoryProvider.get().getTeamById("team1") } throws IllegalStateException("lookup failed")

        val submission = Submission().apply {
            id = "s1"; userId = "u1"; parentId = "exam1@course1"; type = "exam"
            teamId = "team1"
        }

        val result = repository.getExamUploadPayload(submission, null)

        val team = result.getAsJsonObject("team")
        assertEquals("team1", team.get("_id").asString)
        assertNull(team.get("name"))
        assertNull(team.get("type"))
    }

    @Test
    fun `getExamUploadPayload enriches persisted team id with actual local metadata`() = runTest {
        mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "androidId"
        every { NetworkUtils.getDeviceName() } returns "device"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "custom"

        coEvery { teamsRepositoryProvider.get().getTeamById("team1") } returns MyTeam().apply {
            _id = "team1"
            name = "Enterprise One"
            type = "enterprise"
        }

        val submission = Submission().apply {
            id = "s1"; userId = "u1"; parentId = "exam1@course1"; type = "exam"
            teamId = "team1"
        }

        val result = repository.getExamUploadPayload(submission, null)

        val team = result.getAsJsonObject("team")
        assertEquals("team1", team.get("_id").asString)
        assertEquals("Enterprise One", team.get("name").asString)
        assertEquals("enterprise", team.get("type").asString)
    }

    @Test
    fun `getExamUploadPayload uses in-memory team object`() = runTest {
        mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "androidId"
        every { NetworkUtils.getDeviceName() } returns "device"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "custom"

        val submission = Submission().apply {
            id = "s1"; userId = "u1"; parentId = "exam1@course1"; type = "exam"
            teamObject = TeamReference().apply {
                _id = "enterprise1"
                name = "Enterprise One"
                type = "enterprise"
            }
        }

        val result = repository.getExamUploadPayload(submission, null)

        val team = result.getAsJsonObject("team")
        assertEquals("enterprise1", team.get("_id").asString)
        assertEquals("Enterprise One", team.get("name").asString)
        assertEquals("enterprise", team.get("type").asString)
        coVerify(exactly = 0) { teamsRepositoryProvider.get().getTeamById(any()) }
    }

    @Test
    fun `getExamUploadPayload omits the team for a submission with no team`() = runTest {
        mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "androidId"
        every { NetworkUtils.getDeviceName() } returns "device"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "custom"

        val submission = Submission().apply {
            id = "s1"; userId = "u1"; parentId = "exam1@course1"; type = "exam"
        }

        val result = repository.getExamUploadPayload(submission, null)

        assertNull(result.get("team"))
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

    @Test
    fun `markPhotoUploaded delegates single photo to dao`() = runTest {
        repository.markPhotoUploaded("photo1", "rev1", "remote1")
        coVerify { submitPhotosDao.markUploaded("photo1", "rev1", "remote1") }
    }

    @Test
    fun `markPhotoUploaded ignores null photo id`() = runTest {
        repository.markPhotoUploaded(null, "rev1", "remote1")
        coVerify(exactly = 0) { submitPhotosDao.markUploaded(any(), any(), any()) }
    }

    @Test
    fun `markPhotosUploadedBatch delegates batch to dao in one call`() = runTest {
        val uploads = listOf(
            UploadedPhoto("photo1", "rev1", "remote1"),
            UploadedPhoto("photo2", "rev2", "remote2"),
            UploadedPhoto("photo3", "rev3", "remote3")
        )
        repository.markPhotosUploadedBatch(uploads)
        coVerify(exactly = 1) { submitPhotosDao.markUploadedBatch(uploads) }
    }

    @Test
    fun `markPhotosUploadedBatch does not call dao for empty batch`() = runTest {
        repository.markPhotosUploadedBatch(emptyList())
        coVerify(exactly = 0) { submitPhotosDao.markUploadedBatch(any()) }
    }
}
