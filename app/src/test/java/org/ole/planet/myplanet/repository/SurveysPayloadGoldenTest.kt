package org.ole.planet.myplanet.repository

import android.app.Application
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.dao.ExamDao
import org.ole.planet.myplanet.data.room.dao.QuestionDao
import org.ole.planet.myplanet.model.Answer
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.utils.GoldenFixtures.TRICKY
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesGolden
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesRawGolden
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.callPrivate
import org.ole.planet.myplanet.utils.relaxedInstance
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

/**
 * Goldens for the survey-adoption JSON strings (built with org.json, persisted into
 * `submissions.parent` / `submissions.user`) and the public-survey answers payload. Robolectric
 * supplies the real org.json implementation, which escapes `/` as `\/` and never HTML-escapes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SurveysPayloadGoldenTest {

    @Test
    fun `adoption parent and user JSON strings`() {
        val repository = relaxedInstance<SurveysRepositoryImpl>()
        val exam = StepExam(
            id = "exam/1",
            name = TRICKY,
            courseId = null,
            sourcePlanet = "planet",
            isTeamShareAllowed = true,
            noOfQuestions = 3,
            isFromNation = false,
        )
        val user = UserEntity(id = "org.couchdb.user:learner", name = "<learner>")
        assertMatchesRawGolden(
            "repository/surveys/adoption-strings",
            listOf(
                "parent=" + repository.callPrivate("createParentJsonString", exam),
                "parent-empty=" + repository.callPrivate("createParentJsonString", StepExam()),
                "user-team=" + repository.callPrivate("createUserJsonString", user, "planet", true, "team/1"),
                "user-null=" + repository.callPrivate("createUserJsonString", null, "", false, null),
            ).joinToString("\n")
        )
    }

    @Test
    fun `public survey answers payload`() = runTest {
        val surveysRepository = mockk<SurveysRepository>()
        coEvery { surveysRepository.getExamQuestions("survey-1") } returns listOf(
            ExamQuestion(id = "q1", type = "selectMultiple"),
            ExamQuestion(id = "q2", type = "select"),
            ExamQuestion(id = "q3", type = "Select"),
            ExamQuestion(id = "q4", type = "input"),
            ExamQuestion(id = "q5", type = "ratingScale"),
        )
        val submission = Submission(
            answers = mutableListOf(
                Answer(questionId = "q1", valueChoices = listOf("""{"id":"a","text":"<A>"}""", """{"id":"b","text":"B & 'c'","n":1.0}""")),
                Answer(questionId = "q2", valueChoices = listOf("""{"id":"x","text":"X"}""")),
                Answer(questionId = "q3", valueChoices = emptyList(), value = "fallback"),
                Answer(questionId = "q4", value = TRICKY),
            )
        )
        assertMatchesGolden("repository/surveys/public-answers", SurveysPublicMapper(surveysRepository).buildPublicAnswers("survey-1", submission))
    }

    private val publicQuestions = listOf(
        ExamQuestion(id = "q1", type = "selectMultiple"),
        ExamQuestion(id = "q2", type = "select"),
        ExamQuestion(id = "q4", type = "input"),
    )

    private val publicSubmission = Submission(
        answers = mutableListOf(
            Answer(questionId = "q1", valueChoices = listOf("""{"id":"a","text":"<A>","n":1.50}""")),
            Answer(questionId = "q2", valueChoices = listOf("""{"id":"x","text":"X & 'y'"}""")),
            Answer(questionId = "q4", value = TRICKY),
        )
    )

    @Test
    fun `public survey submission body`() = runTest {
        val api = mockk<ApiInterface>()
        val sent = mutableListOf<JsonObject>()
        coEvery { api.postDoc(null, "application/json", any(), any()) } answers {
            sent.add(arg(3))
            Response.success(JsonObject(emptyMap()))
        }
        val repository = relaxedInstance<SurveysRepositoryImpl>(api, TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)))
        val surveysRepository = mockk<SurveysRepository>()
        coEvery { surveysRepository.getExamQuestions("survey-1") } returns publicQuestions
        val mapper = SurveysPublicMapper(surveysRepository)
        val answers = mapper.buildPublicAnswers("survey-1", publicSubmission)
        repository.submitPublicSurvey("https://planet.example/", "team-1", "survey-1", answers,
            mapper.parseRespondent("""{"name":"<n> & 'x'","age":" 42 ","n":1e2,"f":1.50,"none":null}"""))
        repository.submitPublicSurvey("https://planet.example/", "team-1", "survey-1", answers,
            mapper.parseRespondent("""{"age":"unknown","gender":"f"}"""))
        repository.submitPublicSurvey("https://planet.example/", "team-1", "survey-1", answers, mapper.parseRespondent("{}"))
        assertMatchesGolden("repository/surveys/public-submission", JsonArray(sent))
    }

    @Test
    fun `public survey fetched and saved`() = runTest {
        val api = mockk<ApiInterface>()
        coEvery { api.getJsonObject(null, any()) } returns Response.success(
            Json.parseToJsonElement(
                """{"_id":"survey-9","_rev":"1-s","name":"<S> & 'x'","type":"surveys","createdDate":1.7E12,"n":1e2,
                   "questions":[{"id":"q1","type":"select","body":"B & <c>","choices":[{"id":"a","text":"A","n":1.50}],"correctChoice":"a"}]}"""
            ).jsonObject
        )
        val examDao = mockk<ExamDao>(relaxed = true)
        val questionDao = mockk<QuestionDao>(relaxed = true)
        val exams = slot<List<StepExam>>()
        val questions = slot<List<ExamQuestion>>()
        coEvery { examDao.upsertAll(capture(exams)) } returns Unit
        coEvery { questionDao.upsertAll(capture(questions)) } returns Unit
        val repository = relaxedInstance<SurveysRepositoryImpl>(api, TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)), examDao, questionDao)
        val doc = repository.fetchPublicSurvey("https://planet.example", "team-1", "survey-9")
        assertMatchesGolden("repository/surveys/public-fetched", doc)
        repository.saveSurveyFromPublicApi(doc!!)
        assertMatchesRawGolden(
            "repository/surveys/public-saved-columns",
            exams.captured.joinToString("\n") { "exam id=${it.id} name=${it.name} type=${it.type} createdDate=${it.createdDate} questions=${it.noOfQuestions}" } + "\n" +
                questions.captured.joinToString("\n") { "question id=${it.id} examId=${it.examId} choices=${it.choices} correct=${it.correctChoiceList}" }
        )
    }

    @Test
    fun `adopted team survey copies the questions`() = runTest {
        val examDao = mockk<ExamDao>(relaxed = true)
        val questionDao = mockk<QuestionDao>(relaxed = true)
        val teams = mockk<TeamsRepository>(relaxed = true)
        coEvery { examDao.getById("exam-1") } returns StepExam(id = "exam-1", name = "Exam", type = "surveys")
        coEvery { examDao.getByTeamIdAndType("team-1", "surveys") } returns emptyList()
        coEvery { teams.getTeamByIdOrTeamId("team-1") } returns MyTeam(_id = "team-1", name = "Team")
        coEvery { questionDao.getByExamId("exam-1") } returns listOf(
            ExamQuestion(id = "q1", examId = "exam-1", header = "H <1>", body = "B & 'b'", type = "select",
                choices = """[{"id":"a","text":"A & <b>","n":1.50,"e":1e2}]""", correctChoiceList = listOf("a")),
            ExamQuestion(id = "q2", examId = "exam-1", type = "input", choices = "[]"),
        )
        val copied = slot<List<ExamQuestion>>()
        coEvery { questionDao.upsertAll(capture(copied)) } returns Unit
        val repository = relaxedInstance<SurveysRepositoryImpl>(examDao, questionDao, dagger.Lazy { teams })
        repository.adoptSurvey("exam-1", "org.couchdb.user:learner", "team-1", true)
        assertMatchesRawGolden(
            "repository/surveys/adopted-question-copy",
            copied.captured.joinToString("\n") { "header=${it.header} body=${it.body} type=${it.type} choices=${it.choices} correct=${it.correctChoiceList}" }
        )
    }
}
