package org.ole.planet.myplanet.ui.surveys

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.Answer
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.model.Submission

class PublicSurveyPayloadBuilderTest {

    private lateinit var payloadBuilder: PublicSurveyPayloadBuilder

    @Before
    fun setup() {
        payloadBuilder = PublicSurveyPayloadBuilder()
    }

    @Test
    fun `test selectMultiple answer produces array`() {
        val question = ExamQuestion().apply {
            id = "q1"
            type = "selectMultiple"
        }
        val answer = Answer().apply {
            questionId = "q1"
            valueChoices = listOf("{\"text\":\"Choice 1\"}", "{\"text\":\"Choice 2\"}")
        }
        val submission = Submission().apply {
            answers = mutableListOf(answer)
        }

        val result = payloadBuilder.buildPublicAnswers(listOf(question), submission)

        assertEquals(1, result.size())
        assertTrue(result[0].isJsonArray)
        val choicesArray = result[0].asJsonArray
        assertEquals(2, choicesArray.size())
        assertEquals("Choice 1", choicesArray[0].asJsonObject.get("text").asString)
        assertEquals("Choice 2", choicesArray[1].asJsonObject.get("text").asString)
    }

    @Test
    fun `test plain select answer unwraps to single value`() {
        val question = ExamQuestion().apply {
            id = "q1"
            type = "select"
        }
        val answer = Answer().apply {
            questionId = "q1"
            valueChoices = listOf("{\"text\":\"Option A\"}", "{\"text\":\"Option B\"}")
        }
        val submission = Submission().apply {
            answers = mutableListOf(answer)
        }

        val result = payloadBuilder.buildPublicAnswers(listOf(question), submission)

        assertEquals(1, result.size())
        assertTrue(result[0].isJsonObject)
        assertEquals("Option A", result[0].asJsonObject.get("text").asString)
    }

    @Test
    fun `test default question type uses plain value primitive`() {
        val question = ExamQuestion().apply {
            id = "q1"
            type = "text"
        }
        val answer = Answer().apply {
            questionId = "q1"
            value = "Hello World"
        }
        val submission = Submission().apply {
            answers = mutableListOf(answer)
        }

        val result = payloadBuilder.buildPublicAnswers(listOf(question), submission)

        assertEquals(1, result.size())
        assertTrue(result[0].isJsonPrimitive)
        assertEquals("Hello World", result[0].asString)
    }

    @Test
    fun `test answers zipped by questionId not position`() {
        val q1 = ExamQuestion().apply { id = "q1"; type = "text" }
        val q2 = ExamQuestion().apply { id = "q2"; type = "text" }

        val a2 = Answer().apply { questionId = "q2"; value = "Answer 2" }
        val a1 = Answer().apply { questionId = "q1"; value = "Answer 1" }

        val submission = Submission().apply {
            answers = mutableListOf(a2, a1)
        }

        val result = payloadBuilder.buildPublicAnswers(listOf(q1, q2), submission)

        assertEquals(2, result.size())
        assertEquals("Answer 1", result[0].asString)
        assertEquals("Answer 2", result[1].asString)
    }

    @Test
    fun `test sanitizeRespondent trims valid numeric age`() {
        val user = JsonObject().apply {
            addProperty("name", "Alice")
            addProperty("age", " 25 ")
        }

        val result = payloadBuilder.sanitizeRespondent(user)

        assertTrue(result.has("age"))
        assertEquals(25, result.get("age").asInt)
    }

    @Test
    fun `test sanitizeRespondent removes non-numeric age`() {
        val user = JsonObject().apply {
            addProperty("name", "Bob")
            addProperty("age", "invalid_age")
        }

        val result = payloadBuilder.sanitizeRespondent(user)

        assertFalse(result.has("age"))
        assertEquals("Bob", result.get("name").asString)
    }

    @Test
    fun `test sanitizeRespondent leaves object without age untouched`() {
        val user = JsonObject().apply {
            addProperty("name", "Charlie")
        }

        val result = payloadBuilder.sanitizeRespondent(user)

        assertFalse(result.has("age"))
        assertEquals("Charlie", result.get("name").asString)
    }
}
