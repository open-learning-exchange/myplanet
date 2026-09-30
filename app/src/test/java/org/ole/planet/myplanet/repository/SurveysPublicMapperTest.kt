package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.Answer
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.model.Submission

@OptIn(ExperimentalCoroutinesApi::class)
class SurveysPublicMapperTest {

    private lateinit var surveysRepository: SurveysRepository
    private lateinit var publicMapper: SurveysPublicMapper

    @Before
    fun setup() {
        surveysRepository = mockk()
        publicMapper = SurveysPublicMapper(surveysRepository)
    }

    @Test
    fun `buildPublicAnswers selectMultiple produces a nested JsonArray`() = runTest {
        val surveyId = "survey123"
        val q1 = ExamQuestion().apply {
            id = "q1"
            type = "selectMultiple"
        }
        coEvery { surveysRepository.getExamQuestions(surveyId) } returns listOf(q1)

        val choice1 = "{\"id\":\"c1\",\"res\":\"Option A\"}"
        val choice2 = "{\"id\":\"c2\",\"res\":\"Option B\"}"
        val answer = Answer().apply {
            questionId = "q1"
            valueChoices = listOf(choice1, choice2)
        }
        val submission = Submission().apply {
            answers = mutableListOf(answer)
        }

        val result = publicMapper.buildPublicAnswers(surveyId, submission)

        assertEquals(1, result.size())
        assertTrue(result[0].isJsonArray)
        val choicesArray = result[0].asJsonArray
        assertEquals(2, choicesArray.size())
        assertEquals("c1", choicesArray[0].asJsonObject.get("id").asString)
        assertEquals("c2", choicesArray[1].asJsonObject.get("id").asString)
    }

    @Test
    fun `buildPublicAnswers plain select unwraps to a single choice value`() = runTest {
        val surveyId = "survey123"
        val q1 = ExamQuestion().apply {
            id = "q1"
            type = "select"
        }
        coEvery { surveysRepository.getExamQuestions(surveyId) } returns listOf(q1)

        val choice1 = "{\"id\":\"c1\",\"res\":\"Option A\"}"
        val answer = Answer().apply {
            questionId = "q1"
            valueChoices = listOf(choice1)
        }
        val submission = Submission().apply {
            answers = mutableListOf(answer)
        }

        val result = publicMapper.buildPublicAnswers(surveyId, submission)

        assertEquals(1, result.size())
        assertTrue(result[0].isJsonObject)
        assertEquals("c1", result[0].asJsonObject.get("id").asString)
    }

    @Test
    fun `buildPublicAnswers text question fallback produces JsonPrimitive`() = runTest {
        val surveyId = "survey123"
        val q1 = ExamQuestion().apply {
            id = "q1"
            type = "text"
        }
        coEvery { surveysRepository.getExamQuestions(surveyId) } returns listOf(q1)

        val answer = Answer().apply {
            questionId = "q1"
            value = "Hello World"
        }
        val submission = Submission().apply {
            answers = mutableListOf(answer)
        }

        val result = publicMapper.buildPublicAnswers(surveyId, submission)

        assertEquals(1, result.size())
        assertTrue(result[0].isJsonPrimitive)
        assertEquals("Hello World", result[0].asString)
    }

    @Test
    fun `sanitizeRespondent trims valid age string to integer`() {
        val user = JsonObject().apply {
            addProperty("name", "John")
            addProperty("age", " 25 ")
        }

        publicMapper.sanitizeRespondent(user)

        assertTrue(user.has("age"))
        assertTrue(user.get("age").asJsonPrimitive.isNumber)
        assertEquals(25, user.get("age").asInt)
    }

    @Test
    fun `sanitizeRespondent removes non-numeric age property`() {
        val user = JsonObject().apply {
            addProperty("name", "John")
            addProperty("age", "twenty")
        }

        publicMapper.sanitizeRespondent(user)

        assertFalse(user.has("age"))
    }
}
