package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import javax.inject.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.ExamDao
import org.ole.planet.myplanet.data.room.dao.QuestionDao
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.model.MembershipDoc
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DeviceNameProvider
import org.ole.planet.myplanet.utils.NetworkUtils

@OptIn(ExperimentalCoroutinesApi::class)
class SubmissionsPayloadBuilderTest {

    private lateinit var teamsRepositoryProvider: Provider<TeamsRepository>
    private lateinit var teamsRepository: TeamsRepository
    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var examDao: ExamDao
    private lateinit var questionDao: QuestionDao
    private lateinit var deviceNameProvider: DeviceNameProvider
    private val gson = Gson()

    private lateinit var payloadBuilder: SubmissionsPayloadBuilder

    @Before
    fun setUp() {
        teamsRepository = mockk(relaxed = true)
        teamsRepositoryProvider = mockk(relaxed = true)
        every { teamsRepositoryProvider.get() } returns teamsRepository
        sharedPrefManager = mockk(relaxed = true)
        examDao = mockk(relaxed = true)
        questionDao = mockk(relaxed = true)
        deviceNameProvider = mockk(relaxed = true)

        mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "androidId"
        every { NetworkUtils.getDeviceName() } returns "TestDevice"
        every { deviceNameProvider.getCustomDeviceName() } returns "CustomDevice"

        payloadBuilder = SubmissionsPayloadBuilder(
            teamsRepositoryProvider,
            sharedPrefManager,
            examDao,
            questionDao,
            gson,
            deviceNameProvider
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `examUploadPayload takes source and parentCode from SharedPrefManager`() = runTest {
        coEvery { examDao.getById(any()) } returns null
        every { sharedPrefManager.getPlanetCode() } returns "pref_planet"
        every { sharedPrefManager.getParentCode() } returns "pref_parent"

        val submission = Submission().apply {
            id = "s1"; parentId = "exam1@course1"; type = "exam"
        }

        val result = payloadBuilder.examUploadPayload(submission, null)

        assertEquals("pref_planet", result.get("source").asString)
        assertEquals("pref_parent", result.get("parentCode").asString)
    }

    @Test
    fun `submissionPayload applies empty survey pending defaults for null fields`() = runTest {
        coEvery { examDao.getById(any()) } returns null
        val submission = Submission().apply {
            id = "s1"
            parentId = null
            type = null
            status = null
        }

        val result = payloadBuilder.submissionPayload(submission, "src", "pCode", null)

        assertEquals("", result.get("parentId").asString)
        assertEquals("survey", result.get("type").asString)
        assertEquals("pending", result.get("status").asString)
    }

    @Test
    fun `submissionPayload adds membershipDoc teamId to user JSON but examUploadPayload does not`() = runTest {
        coEvery { examDao.getById(any()) } returns null
        val user = UserEntity().apply {
            id = "user1"
            name = "Test User"
        }
        val submission = Submission().apply {
            id = "s1"
            parentId = "p1"
            membershipDoc = MembershipDoc().apply { teamId = "team123" }
        }

        val submissionResult = payloadBuilder.submissionPayload(submission, "src", "pCode", user)
        val examResult = payloadBuilder.examUploadPayload(submission, user)

        val submissionUserJson = submissionResult.getAsJsonObject("user")
        assertTrue(submissionUserJson.has("membershipDoc"))
        assertEquals("team123", submissionUserJson.getAsJsonObject("membershipDoc").get("teamId").asString)

        val examUserJson = examResult.getAsJsonObject("user")
        assertFalse(examUserJson.has("membershipDoc"))
    }

    @Test
    fun `when exam exists parent comes from StepExam serializeExam`() = runTest {
        val exam = StepExam().apply {
            id = "exam1"
            name = "Test Exam"
        }
        val question = ExamQuestion().apply {
            id = "q1"
            examId = "exam1"
            header = "Question 1"
        }

        coEvery { examDao.getById("exam1") } returns exam
        coEvery { questionDao.getByExamId("exam1") } returns listOf(question)

        val submission = Submission().apply {
            id = "s1"
            parentId = "exam1@course1"
            parent = "{\"fallback\":\"parent\"}"
        }

        val examResult = payloadBuilder.examUploadPayload(submission, null)
        val submissionResult = payloadBuilder.submissionPayload(submission, "src", "pCode", null)

        val expectedParent = StepExam.serializeExam(exam, listOf(question))
        assertEquals(expectedParent, examResult.getAsJsonObject("parent"))
        assertEquals(expectedParent, submissionResult.getAsJsonObject("parent"))
    }

    @Test
    fun `for fixed submission each method toString matches golden JSON string with key order`() = runTest {
        coEvery { examDao.getById(any()) } returns null
        every { sharedPrefManager.getPlanetCode() } returns "planet1"
        every { sharedPrefManager.getParentCode() } returns "parent1"

        val submission = Submission().apply {
            _id = "doc123"
            _rev = "1-abc"
            parentId = "p1"
            type = "exam"
            grade = 100
            startTime = 1000L
            lastUpdateTime = 2000L
            status = "complete"
            sender = "sender1"
            parent = "{\"name\":\"Parent Exam\"}"
            user = "{\"name\":\"Test User\"}"
        }

        val examPayload = payloadBuilder.examUploadPayload(submission, null)
        val expectedExamJson = "{\"_id\":\"doc123\",\"_rev\":\"1-abc\",\"parentId\":\"p1\",\"type\":\"exam\",\"grade\":100,\"startTime\":1000,\"lastUpdateTime\":2000,\"status\":\"complete\",\"androidId\":\"androidId\",\"app\":\"myplanet\",\"deviceName\":\"TestDevice\",\"customDeviceName\":\"CustomDevice\",\"sender\":\"sender1\",\"source\":\"planet1\",\"parentCode\":\"parent1\",\"answers\":[],\"parent\":{\"name\":\"Parent Exam\"},\"user\":{\"name\":\"Test User\"}}"

        assertEquals(expectedExamJson, examPayload.toString())

        val submissionPayload = payloadBuilder.submissionPayload(submission, "argSource", "argParentCode", null)
        val expectedSubmissionJson = "{\"_id\":\"doc123\",\"_rev\":\"1-abc\",\"parentId\":\"p1\",\"type\":\"exam\",\"grade\":100,\"startTime\":1000,\"lastUpdateTime\":2000,\"status\":\"complete\",\"androidId\":\"androidId\",\"app\":\"myplanet\",\"deviceName\":\"TestDevice\",\"customDeviceName\":\"CustomDevice\",\"sender\":\"sender1\",\"source\":\"argSource\",\"parentCode\":\"argParentCode\",\"answers\":[],\"parent\":{\"name\":\"Parent Exam\"},\"user\":{\"name\":\"Test User\"}}"

        assertEquals(expectedSubmissionJson, submissionPayload.toString())
    }

    @Test
    fun `getTeamByIdOrNull rethrows CancellationException and returns null on other exceptions`() = runTest {
        coEvery { teamsRepository.getTeamById("cancel_team") } throws CancellationException("cancelled")
        coEvery { teamsRepository.getTeamById("error_team") } throws RuntimeException("network error")

        var threwCancellation = false
        try {
            payloadBuilder.getTeamByIdOrNull("cancel_team")
        } catch (e: CancellationException) {
            threwCancellation = true
        }
        assertTrue(threwCancellation)

        val nullResult = payloadBuilder.getTeamByIdOrNull("error_team")
        assertNull(nullResult)
    }
}
