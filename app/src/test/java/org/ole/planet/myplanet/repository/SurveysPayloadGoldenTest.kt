package org.ole.planet.myplanet.repository

import android.app.Application
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.Answer
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.utils.GoldenFixtures.TRICKY
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesGolden
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesRawGolden
import org.ole.planet.myplanet.utils.callPrivate
import org.ole.planet.myplanet.utils.relaxedInstance
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
}
