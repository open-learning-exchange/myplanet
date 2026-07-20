package org.ole.planet.myplanet.repository

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.SubmitPhotosDao
import org.ole.planet.myplanet.data.room.dao.legacy.ExamDao
import org.ole.planet.myplanet.data.room.dao.legacy.QuestionDao
import org.ole.planet.myplanet.data.room.dao.legacy.SubmissionDao
import org.ole.planet.myplanet.data.room.dao.legacy.UserDao
import org.ole.planet.myplanet.data.room.dao.legacy.AnswerDao
import org.ole.planet.myplanet.data.room.entity.legacy.RoomExamEntity
import org.ole.planet.myplanet.data.room.entity.legacy.RoomSubmissionEntity
import org.ole.planet.myplanet.model.CreateExamSubmissionRequest
import org.ole.planet.myplanet.model.ExamAnswerData
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.services.SharedPrefManager

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
    private val userDao: UserDao = mockk(relaxed = true)
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
            surveysRepositoryProvider,
            context,
            sharedPrefManager,
            exporter,
            submitPhotosDao,
            submissionDao,
            answerDao,
            examDao,
            questionDao,
            userDao
        ), recordPrivateCalls = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getPendingSurveysFlow queries correctly`() = runTest {
        every { submissionDao.observePendingSurveys("user_123") } returns kotlinx.coroutines.flow.flowOf(listOf(RoomSubmissionEntity(id = "submission1")))

        val result = repository.getPendingSurveysFlow("user_123").first()
        assertEquals(1, result.size)
    }

    @Test
    fun `getSubmissionsFlow queries correctly`() = runTest {
        every { submissionDao.observeByUserId("user_123") } returns kotlinx.coroutines.flow.flowOf(listOf(RoomSubmissionEntity(id = "submission1")))

        val result = repository.getSubmissionsFlow("user_123").first()
        assertEquals(1, result.size)
    }

    @Test
    fun `getSubmissionsFlow suppresses equivalent emissions`() = runTest {
        val subList = listOf(RoomSubmissionEntity(id = "1", lastUpdateTime = 100L))
        val subListDup = listOf(RoomSubmissionEntity(id = "1", lastUpdateTime = 100L))

        val flowEmitter = kotlinx.coroutines.flow.MutableSharedFlow<List<RoomSubmissionEntity>>(replay = 1)
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
            RoomSubmissionEntity(id = "sub1", parentId = "exam1@course1"),
            RoomSubmissionEntity(id = "sub2", parentId = "exam2@course1"),
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
        coEvery { submissionDao.getByUserId("test") } returns listOf(RoomSubmissionEntity(id = "submission1", userId = "test"))
        coEvery { answerDao.getBySubmissionIds(listOf("submission1")) } returns emptyList()

        val result = repository.getSubmissionsByUserId("test")
        assertEquals(1, result.size)
    }

    @Test
    fun `createBulkSurveySubmissions calls getOrCreateSubmission for all users`() = runTest {
        val examId = "examId"
        val userIds = listOf("user1", "user2")
        coEvery { examDao.getById(examId) } returns RoomExamEntity(id = examId, courseId = "courseId")

        coEvery { repository.getOrCreateSubmission(any(), any()) } returns mockk()

        repository.createBulkSurveySubmissions(examId, userIds)

        coVerify(exactly = 1) { repository.getOrCreateSubmission("user1", "examId@courseId") }
        coVerify(exactly = 1) { repository.getOrCreateSubmission("user2", "examId@courseId") }
    }

    @Test
    fun `saveSubmission upserts submission through Room`() = runTest {
        val sub = RealmSubmission().apply { id = "submission1" }

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
        coEvery { submissionDao.getByParentUserAndStatus("exam@course", "user", null) } returns listOf(RoomSubmissionEntity(id = "submission1"))

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
        val exam = mockk<RealmStepExam>(relaxed = true)
        every { exam.courseId } returns "course_id"
        every { exam.id } returns "exam_id"

        val result = repository.createExamSubmission(
            CreateExamSubmissionRequest("user", "dob", "gender", exam, "type", null)
        )

        assertEquals("exam_id@course_id", result?.parentId)
        coVerify { submissionDao.upsertAll(match { it.single().parentId == "exam_id@course_id" }) }
    }

    @Test
    fun `saveExamAnswer upserts answer through Room`() = runTest {
        val answerData = mockk<ExamAnswerData>(relaxed = true)
        val question = RealmExamQuestion().apply { id = "question1"; examId = "exam1"; type = "text" }
        val submission = RealmSubmission().apply { id = "submission1"; userId = "user1"; parentId = "exam1@course1" }

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
        coEvery { submissionDao.getByIdOrRemoteId("submission1") } returns RoomSubmissionEntity(id = "submission1", parentId = "exam1@course1", userId = "user1")

        val result = repository.saveExamAnswer(answerData)

        assertTrue(result)
        coVerify { answerDao.upsertAll(match { it.single().submissionId == "submission1" && it.single().value == "answer text" }) }
        coVerify { submissionDao.updateStatusAndLastUpdate("submission1", "complete", any()) }
    }

    @Test
    fun `markSubmissionComplete updates submission through Room`() = runTest {
        val payload = JsonObject().apply { addProperty("name", "Learner") }

        repository.markSubmissionComplete("test_id", payload)

        coVerify { submissionDao.markComplete("test_id", payload.toString()) }
    }
}
