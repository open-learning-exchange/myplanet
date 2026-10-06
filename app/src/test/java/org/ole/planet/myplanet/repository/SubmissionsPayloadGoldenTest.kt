package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import javax.inject.Provider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.AnswerDao
import org.ole.planet.myplanet.data.room.dao.ExamDao
import org.ole.planet.myplanet.data.room.dao.QuestionDao
import org.ole.planet.myplanet.data.room.dao.SubmissionDao
import org.ole.planet.myplanet.model.Answer
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.model.MembershipDoc
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.model.TeamReference
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DeviceNameProvider
import org.ole.planet.myplanet.utils.GoldenFixtures
import org.ole.planet.myplanet.utils.GoldenFixtures.BIG_LONG
import org.ole.planet.myplanet.utils.GoldenFixtures.TRICKY
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesGolden
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesRawGolden
import org.ole.planet.myplanet.utils.callPrivate
import org.ole.planet.myplanet.utils.relaxedInstance
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx

/** Goldens for submission/exam-result upload documents and the JSON columns synced submissions persist. */
class SubmissionsPayloadGoldenTest {

    private val sharedPrefManager = mockk<SharedPrefManager>(relaxed = true)
    private val examDao = mockk<ExamDao>(relaxed = true)
    private val questionDao = mockk<QuestionDao>(relaxed = true)
    private val submissionDao = mockk<SubmissionDao>(relaxed = true)
    private val answerDao = mockk<AnswerDao>(relaxed = true)
    private val deviceNameProvider = mockk<DeviceNameProvider>()
    private val teamsRepository = mockk<TeamsRepository>()
    private lateinit var repository: SubmissionsRepositoryImpl

    @Before
    fun setUp() {
        GoldenFixtures.mockDeviceIdentity()
        every { sharedPrefManager.getPlanetCode() } returns "planet"
        every { sharedPrefManager.getParentCode() } returns "parent"
        every { deviceNameProvider.getCustomDeviceName() } returns GoldenFixtures.CUSTOM_DEVICE_NAME
        every { deviceNameProvider.getDeviceName() } returns GoldenFixtures.DEVICE_NAME
        coEvery { teamsRepository.getTeamById("local-team") } returns MyTeam(_id = "local-team", name = "Local <team>", type = "team")
        coEvery { examDao.getById(any()) } returns null
        coEvery { examDao.getById("exam-1") } returns StepExam(
            id = "exam-1",
            _rev = "1-e",
            name = TRICKY,
            type = "exam",
            totalMarks = 5,
            createdDate = BIG_LONG,
        )
        coEvery { questionDao.getByExamId("exam-1") } returns listOf(
            ExamQuestion(id = "q1", header = "H", body = "B <1>", type = "select", choices = """[{"id":"a","text":"A & B"}]""", correctChoiceList = listOf("a")),
        )
        val provider = mockk<Provider<TeamsRepository>>()
        every { provider.get() } returns teamsRepository
        repository = relaxedInstance(provider, sharedPrefManager, examDao, questionDao, submissionDao, answerDao, Gson(), deviceNameProvider)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun golden(name: String, actual: Any?) = assertMatchesGolden("repository/submissions/$name", actual)

    private fun user() = UserEntity(
        id = "org.couchdb.user:learner",
        _id = "org.couchdb.user:learner",
        _rev = "1-u",
        name = "learner",
        rolesList = listOf("learner"),
        firstName = TRICKY,
        planetCode = "planet",
        parentCode = "parent",
        iterations = "10",
    )

    private fun answers() = mutableListOf(
        Answer(id = "a1", value = "<answer> & 'it'", mistakes = 1, isPassed = true, questionId = "q1"),
        Answer(id = "a2", valueChoices = listOf("""{"id":"a","text":"A & B"}"""), questionId = "q2"),
    )

    private fun examSubmission() = Submission(
        id = "sub-1",
        _id = "sub-1",
        _rev = "2-s",
        parentId = "exam-1@course-1",
        type = "exam",
        startTime = BIG_LONG,
        lastUpdateTime = 1_717_171_717_171L,
        grade = 3,
        status = "complete",
        sender = "learner",
        answers = answers(),
        teamObject = TeamReference().apply {
            _id = "team-1"
            name = "Team <1>"
            type = "team"
        },
    )

    private fun surveySubmission() = Submission(
        id = "sub-2",
        parentId = "survey-x",
        user = """{"_id":"org.couchdb.user:old","name":"old <user>","age":1.0}""",
        parent = """{"_id":"survey-x","name":"Survey & 'more'","questions":[]}""",
        teamId = "local-team",
        membershipDoc = MembershipDoc().apply { teamId = "local-team" },
        answers = answers(),
    )

    @Test
    fun `serializeSubmission`() = runTest {
        golden("serializeSubmission-exam", repository.serializeSubmission(examSubmission(), "planet", "parent", user()))
        golden("serializeSubmission-survey-stored-user", repository.serializeSubmission(surveySubmission(), "src", "", null))
        golden("serializeSubmission-empty", repository.serializeSubmission(Submission(), "", "", null))
    }

    @Test
    fun `getExamUploadPayload`() = runTest {
        golden("examUpload-exam", repository.getExamUploadPayload(examSubmission(), user()))
        golden("examUpload-survey-stored-user", repository.getExamUploadPayload(surveySubmission(), null))
        golden("examUpload-empty", repository.getExamUploadPayload(Submission(), null))
    }

    @Test
    fun `synced submission persists user and parent JSON columns`() {
        val doc = Json.parseToJsonElement(
            """
            {"_id":"sub-9","_rev":"1-x","parentId":"exam-1@course-1","type":"exam","status":"complete",
             "startTime":1717171717171,"lastUpdateTime":1.7171717E12,"grade":2.5,
             "user":{"_id":"learner@planet","name":"<learner>","_attachments":{"img":{"data":"AAAA"}},"membershipDoc":{"teamId":"t1"},"age":30,"ratio":0.1,"none":null},
             "parent":{"_id":"exam-1","name":"Exam & 'quiz'","n":1.0,"e":1e3},
             "answers":[{"value":"<v>","mistakes":"2","passed":"true","questionId":""},{"value":["a",{"id":"b"}]},{"value":{"x":1}},{"value":null},{"value":5}]}
            """.trimIndent()
        ).jsonObject.toGson()
        val submissions = slot<List<Submission>>()
        val answers = slot<List<Answer>>()
        every { submissionDao.upsertAllBlocking(capture(submissions)) } returns Unit
        every { answerDao.upsertAllBlocking(capture(answers)) } returns Unit

        repository.callPrivate("upsertRoomSubmissionsFromSync", listOf(doc))

        val stored = submissions.captured.single()
        assertMatchesRawGolden(
            "repository/submissions/synced-columns",
            listOf(
                "userId=${stored.userId}",
                "user=${stored.user}",
                "parent=${stored.parent}",
                "grade=${stored.grade}",
                "lastUpdateTime=${stored.lastUpdateTime}",
                "teamId=${stored.teamId}",
            ).joinToString("\n") + "\n" + answers.captured.joinToString("\n") {
                "answer ${it.id} value=${it.value} valueChoices=${it.valueChoices} mistakes=${it.mistakes} passed=${it.isPassed} questionId=${it.questionId}"
            }
        )
        // GsonUtils.getJsonObject hands back the live child, so the remove("_attachments") above also edits the source doc.
        golden("synced-source-after-upsert", doc.toKotlinx())
    }
}
