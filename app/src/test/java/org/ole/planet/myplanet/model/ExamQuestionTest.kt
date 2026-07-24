package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamQuestionTest {

    @Test
    fun testInsertExamQuestions_emptyArray() {
        val emptyArray = JsonArray()

        val result = ExamQuestion.insertExamQuestions(emptyArray, "exam123")

        assertTrue(result.isEmpty())
    }

    @Test
    fun testInsertExamQuestions_newQuestions() {
        val questionsArray = JsonArray()

        val question1 = JsonObject()
        question1.addProperty("id", "q1")
        question1.addProperty("body", "Body 1")
        question1.addProperty("type", "select")
        question1.addProperty("title", "Header 1")
        question1.addProperty("marks", "5")
        question1.addProperty("hasOtherOption", false)

        val choicesArray = JsonArray()
        val choice1 = JsonObject()
        choice1.addProperty("res", "Choice A")
        choice1.addProperty("id", "c1")
        choicesArray.add(choice1)
        question1.add("choices", choicesArray)
        question1.addProperty("correctChoice", "c1")

        questionsArray.add(question1)

        val insertedQuestions = ExamQuestion.insertExamQuestions(questionsArray, "exam123")

        assertEquals(1, insertedQuestions.size)
        val insertedQuestion = insertedQuestions[0]
        assertEquals("exam123", insertedQuestion.examId)
        assertEquals("Body 1", insertedQuestion.body)
        assertEquals("select", insertedQuestion.type)
        assertEquals("Header 1", insertedQuestion.header)
        assertEquals("5", insertedQuestion.marks)
        assertFalse(insertedQuestion.hasOtherOption)
        assertNotNull(insertedQuestion.getCorrectChoice())
        assertEquals(1, insertedQuestion.getCorrectChoice()?.size)
        assertEquals("Choice A", insertedQuestion.getCorrectChoice()?.get(0))
    }

    @Test
    fun testInsertExamQuestions_emptyList() {
        val questions = JsonArray()
        val result = ExamQuestion.insertExamQuestions(questions, "exam1")
        assertTrue(result.isEmpty())
    }

    @Test
    fun testInsertExamQuestions_nonEmptyList() {
        val questions = JsonArray()

        val q1 = JsonObject().apply {
            addProperty("id", "q1")
            addProperty("body", "Body1")
            addProperty("type", "select")
            addProperty("title", "Title1")
            addProperty("marks", "1")
            addProperty("hasOtherOption", false)
            val choices = JsonArray().apply {
                add(JsonObject().apply { addProperty("id", "c1"); addProperty("res", "Choice A") })
                add(JsonObject().apply { addProperty("id", "c2"); addProperty("res", "Choice B") })
            }
            add("choices", choices)
            addProperty("correctChoice", "c1")
        }

        val q2 = JsonObject().apply {
            addProperty("id", "q2")
            addProperty("body", "Body2")
            addProperty("type", "selectMultiple")
            addProperty("title", "Title2")
            addProperty("marks", "2")
            addProperty("hasOtherOption", true)
            val choices = JsonArray().apply {
                add(JsonObject().apply { addProperty("id", "c3"); addProperty("res", "Choice C") })
            }
            add("choices", choices)
            val correctChoicesArray = JsonArray().apply {
                add("Choice C")
                add("Choice D")
            }
            add("correctChoice", correctChoicesArray)
        }

        questions.add(q1)
        questions.add(q2)

        val insertedQuestions = ExamQuestion.insertExamQuestions(questions, "examId")
        assertEquals(2, insertedQuestions.size)

        val mockQ1 = insertedQuestions[0]
        val mockQ2 = insertedQuestions[1]

        assertEquals("examId", mockQ1.examId)
        assertEquals("Body1", mockQ1.body)
        assertEquals("select", mockQ1.type)
        assertEquals("Title1", mockQ1.header)
        assertEquals("1", mockQ1.marks)
        assertFalse(mockQ1.hasOtherOption)
        assertNotNull(mockQ1.getCorrectChoice())
        assertEquals(1, mockQ1.getCorrectChoice()?.size)
        assertEquals("Choice A", mockQ1.getCorrectChoice()?.get(0))
        assertEquals(1, mockQ1.correctChoiceArray.size())
        assertEquals("Choice A", mockQ1.correctChoiceArray.get(0).asString)

        assertEquals("examId", mockQ2.examId)
        assertEquals("Body2", mockQ2.body)
        assertEquals("selectMultiple", mockQ2.type)
        assertEquals("Title2", mockQ2.header)
        assertEquals("2", mockQ2.marks)
        assertTrue(mockQ2.hasOtherOption)
        assertNotNull(mockQ2.getCorrectChoice())
        assertEquals(2, mockQ2.getCorrectChoice()?.size)
        assertEquals("choice c", mockQ2.getCorrectChoice()?.get(0))
        assertEquals("choice d", mockQ2.getCorrectChoice()?.get(1))
    }

    @Test
    fun testSerializeQuestions_withRealData() {
        val questions = JsonArray()
        val q1 = JsonObject().apply {
            addProperty("id", "q1")
            addProperty("body", "Body1")
            addProperty("type", "select")
            addProperty("title", "Title1")
            addProperty("marks", "1")
            addProperty("hasOtherOption", false)
            val choices = JsonArray().apply {
                add(JsonObject().apply { addProperty("id", "c1"); addProperty("res", "Choice A") })
            }
            add("choices", choices)
            add("correctChoice", JsonArray().apply { add("Choice A") })
        }
        questions.add(q1)

        val insertedQuestions = ExamQuestion.insertExamQuestions(questions, "exam1")
        val serialized = ExamQuestion.serializeQuestions(insertedQuestions)

        assertNotNull(serialized)
        assertEquals(1, serialized.size())
        assertEquals("Title1", serialized[0].asJsonObject.get("header").asString)
        assertEquals("Body1", serialized[0].asJsonObject.get("body").asString)
    }
}
