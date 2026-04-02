package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.*
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmQuery
import io.realm.RealmResults
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.utils.JsonUtils
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

class RealmExamQuestionTest {

    private lateinit var realmExamQuestion: RealmExamQuestion
    private lateinit var mockRealm: Realm

    @Before
    fun setUp() {
        // Suppress the MockK warning: "RealmResults should not be mocked! Consider refactoring your test."
        Logger.getLogger("io.mockk.impl.log.JULLogger").level = Level.OFF
        realmExamQuestion = RealmExamQuestion()
        mockRealm = mockk(relaxed = true)
    }

    @Test
    fun testRealmExamQuestionProperties() {
        realmExamQuestion.id = "test_id"
        assertEquals("test_id", realmExamQuestion.id)

        realmExamQuestion.header = "test_header"
        assertEquals("test_header", realmExamQuestion.header)

        realmExamQuestion.body = "test_body"
        assertEquals("test_body", realmExamQuestion.body)

        realmExamQuestion.type = "test_type"
        assertEquals("test_type", realmExamQuestion.type)

        realmExamQuestion.examId = "test_exam_id"
        assertEquals("test_exam_id", realmExamQuestion.examId)

        realmExamQuestion.marks = "10"
        assertEquals("10", realmExamQuestion.marks)

        realmExamQuestion.choices = "[\"A\", \"B\"]"
        assertEquals("[\"A\", \"B\"]", realmExamQuestion.choices)

        realmExamQuestion.hasOtherOption = true
        assertTrue(realmExamQuestion.hasOtherOption)
    }

    @Test
    fun testGetCorrectChoiceArray_nullCorrectChoice() {
        val correctChoiceArray = realmExamQuestion.correctChoiceArray
        assertNotNull(correctChoiceArray)
        assertTrue(correctChoiceArray.isEmpty)
    }

    @Test
    fun testGetCorrectChoiceArray_populated() {
        // Need to test correctChoiceArray when populated via insertCorrectChoice
        val questionArray = JsonArray()
        val questionObject = JsonObject()
        questionObject.addProperty("id", "q_id")
        questionObject.addProperty("type", "select")

        val choicesArray = JsonArray()
        val choice1 = JsonObject()
        choice1.addProperty("id", "c1")
        choicesArray.add(choice1)
        questionObject.add("choices", choicesArray)

        val correctChoices = JsonArray()
        correctChoices.add("Option 1")
        correctChoices.add("Option 2")
        questionObject.add("correctChoice", correctChoices)

        questionArray.add(questionObject)

        val mockQuery = mockk<RealmQuery<RealmExamQuestion>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmExamQuestion>>(relaxed = true)
        every { mockRealm.where(RealmExamQuestion::class.java) } returns mockQuery
        every { mockQuery.`in`("id", arrayOf("q_id")) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        every { mockResults.iterator() } returns mutableListOf<RealmExamQuestion>().iterator()

        val newQuestion = RealmExamQuestion()
        every { mockRealm.createObject(RealmExamQuestion::class.java, "q_id") } returns newQuestion

        RealmExamQuestion.insertExamQuestions(questionArray, "exam_id", mockRealm)

        val correctChoiceArray = newQuestion.correctChoiceArray
        assertEquals(2, correctChoiceArray.size())
        assertEquals("option 1", correctChoiceArray.get(0).asString)
        assertEquals("option 2", correctChoiceArray.get(1).asString)
    }

    @Test
    fun testGetCorrectChoice() {
        assertNull(realmExamQuestion.getCorrectChoice())
    }

    @Test
    fun testInsertExamQuestions_emptyArray() {
        val emptyArray = JsonArray()
        RealmExamQuestion.insertExamQuestions(emptyArray, "exam_id", mockRealm)
        verify(exactly = 0) { mockRealm.where(RealmExamQuestion::class.java) }
    }

    @Test
    fun testInsertExamQuestions_noIdFallback() {
        val questionArray = JsonArray()
        val questionObject = JsonObject()
        // Omit "id" to trigger fallback: "$examId-${i}"
        questionObject.addProperty("body", "Test fallback body")
        questionObject.addProperty("type", "text")
        questionArray.add(questionObject)

        val expectedFallbackId = "exam_id-0"

        val mockQuery = mockk<RealmQuery<RealmExamQuestion>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmExamQuestion>>(relaxed = true)
        every { mockRealm.where(RealmExamQuestion::class.java) } returns mockQuery
        every { mockQuery.`in`("id", arrayOf(expectedFallbackId)) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        every { mockResults.iterator() } returns mutableListOf<RealmExamQuestion>().iterator()

        val newQuestion = RealmExamQuestion()
        every { mockRealm.createObject(RealmExamQuestion::class.java, expectedFallbackId) } returns newQuestion

        RealmExamQuestion.insertExamQuestions(questionArray, "exam_id", mockRealm)

        assertEquals("Test fallback body", newQuestion.body)
        verify { mockRealm.createObject(RealmExamQuestion::class.java, expectedFallbackId) }
    }

    @Test
    fun testInsertExamQuestions_newQuestion() {
        val questionArray = JsonArray()
        val questionObject = JsonObject()
        questionObject.addProperty("id", "q_id")
        questionObject.addProperty("body", "Test body")
        questionObject.addProperty("type", "text")
        questionObject.addProperty("title", "Test title")
        questionObject.addProperty("marks", "5")
        questionObject.addProperty("hasOtherOption", false)
        questionArray.add(questionObject)

        val mockQuery = mockk<RealmQuery<RealmExamQuestion>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmExamQuestion>>(relaxed = true)
        every { mockRealm.where(RealmExamQuestion::class.java) } returns mockQuery
        every { mockQuery.`in`("id", arrayOf("q_id")) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        every { mockResults.iterator() } returns mutableListOf<RealmExamQuestion>().iterator()

        val newQuestion = RealmExamQuestion()
        every { mockRealm.createObject(RealmExamQuestion::class.java, "q_id") } returns newQuestion

        RealmExamQuestion.insertExamQuestions(questionArray, "exam_id", mockRealm)

        assertEquals("exam_id", newQuestion.examId)
        assertEquals("Test body", newQuestion.body)
        assertEquals("text", newQuestion.type)
        assertEquals("Test title", newQuestion.header)
        assertEquals("5", newQuestion.marks)
        assertEquals("[]", newQuestion.choices)
        assertFalse(newQuestion.hasOtherOption)
    }

    @Test
    fun testInsertExamQuestions_existingQuestion() {
        val questionArray = JsonArray()
        val questionObject = JsonObject()
        questionObject.addProperty("id", "q_id")
        questionObject.addProperty("body", "Updated body")
        questionArray.add(questionObject)

        val mockQuery = mockk<RealmQuery<RealmExamQuestion>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmExamQuestion>>(relaxed = true)
        every { mockRealm.where(RealmExamQuestion::class.java) } returns mockQuery
        every { mockQuery.`in`("id", arrayOf("q_id")) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults

        val existingQuestion = RealmExamQuestion()
        existingQuestion.id = "q_id"
        every { mockResults.iterator() } returns mutableListOf(existingQuestion).iterator()

        RealmExamQuestion.insertExamQuestions(questionArray, "exam_id", mockRealm)

        assertEquals("Updated body", existingQuestion.body)
        verify(exactly = 0) { mockRealm.createObject(RealmExamQuestion::class.java, any<String>()) }
    }

    @Test
    fun testInsertExamQuestions_multipleChoice() {
        val questionArray = JsonArray()
        val questionObject = JsonObject()
        questionObject.addProperty("id", "q_id")
        questionObject.addProperty("type", "select")

        val choicesArray = JsonArray()
        val choice1 = JsonObject()
        choice1.addProperty("id", "c1")
        choice1.addProperty("res", "Choice A")
        choicesArray.add(choice1)
        questionObject.add("choices", choicesArray)

        questionObject.addProperty("correctChoice", "c1")

        questionArray.add(questionObject)

        val mockQuery = mockk<RealmQuery<RealmExamQuestion>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmExamQuestion>>(relaxed = true)
        every { mockRealm.where(RealmExamQuestion::class.java) } returns mockQuery
        every { mockQuery.`in`("id", arrayOf("q_id")) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        every { mockResults.iterator() } returns mutableListOf<RealmExamQuestion>().iterator()

        val newQuestion = RealmExamQuestion()
        every { mockRealm.createObject(RealmExamQuestion::class.java, "q_id") } returns newQuestion

        RealmExamQuestion.insertExamQuestions(questionArray, "exam_id", mockRealm)

        assertNotNull(newQuestion.choices)
        val expectedChoices = JsonUtils.gson.toJson(choicesArray)
        assertEquals(expectedChoices, newQuestion.choices)

        assertNotNull(newQuestion.getCorrectChoice())
        assertEquals(1, newQuestion.getCorrectChoice()?.size)
        assertEquals("Choice A", newQuestion.getCorrectChoice()?.get(0))
    }

    @Test
    fun testInsertExamQuestions_correctChoiceNoMatch() {
        val questionArray = JsonArray()
        val questionObject = JsonObject()
        questionObject.addProperty("id", "q_id")
        questionObject.addProperty("type", "select")

        val choicesArray = JsonArray()
        val choice1 = JsonObject()
        choice1.addProperty("id", "c1")
        choice1.addProperty("res", "Choice A")
        choicesArray.add(choice1)
        questionObject.add("choices", choicesArray)

        // Use a string that doesn't match the choice id "c1"
        questionObject.addProperty("correctChoice", "c2")

        questionArray.add(questionObject)

        val mockQuery = mockk<RealmQuery<RealmExamQuestion>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmExamQuestion>>(relaxed = true)
        every { mockRealm.where(RealmExamQuestion::class.java) } returns mockQuery
        every { mockQuery.`in`("id", arrayOf("q_id")) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        every { mockResults.iterator() } returns mutableListOf<RealmExamQuestion>().iterator()

        val newQuestion = RealmExamQuestion()
        every { mockRealm.createObject(RealmExamQuestion::class.java, "q_id") } returns newQuestion

        RealmExamQuestion.insertExamQuestions(questionArray, "exam_id", mockRealm)

        // The correct choice should remain null/empty as no match was found
        assertNull(newQuestion.getCorrectChoice())
    }

    @Test
    fun testInsertExamQuestions_multipleChoice_arrayCorrectChoice() {
        val questionArray = JsonArray()
        val questionObject = JsonObject()
        questionObject.addProperty("id", "q_id")
        questionObject.addProperty("type", "select")

        val choicesArray = JsonArray()
        val choice1 = JsonObject()
        choice1.addProperty("id", "c1")
        choicesArray.add(choice1)
        questionObject.add("choices", choicesArray)

        val correctChoices = JsonArray()
        correctChoices.add("Option 1")
        correctChoices.add("Option 2")
        questionObject.add("correctChoice", correctChoices)

        questionArray.add(questionObject)

        val mockQuery = mockk<RealmQuery<RealmExamQuestion>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmExamQuestion>>(relaxed = true)
        every { mockRealm.where(RealmExamQuestion::class.java) } returns mockQuery
        every { mockQuery.`in`("id", arrayOf("q_id")) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        every { mockResults.iterator() } returns mutableListOf<RealmExamQuestion>().iterator()

        val newQuestion = RealmExamQuestion()
        every { mockRealm.createObject(RealmExamQuestion::class.java, "q_id") } returns newQuestion

        RealmExamQuestion.insertExamQuestions(questionArray, "exam_id", mockRealm)

        assertNotNull(newQuestion.getCorrectChoice())
        assertEquals(2, newQuestion.getCorrectChoice()?.size)
        assertEquals("option 1", newQuestion.getCorrectChoice()?.get(0))
        assertEquals("option 2", newQuestion.getCorrectChoice()?.get(1))
    }

    @Test
    fun testSerializeQuestions() {
        val results = mockk<RealmResults<RealmExamQuestion>>(relaxed = true)

        val question1 = RealmExamQuestion()
        question1.header = "H1"
        question1.body = "B1"
        question1.type = "text"
        question1.marks = "5"
        question1.choices = "[\"A\"]"
        question1.hasOtherOption = true

        val question2 = RealmExamQuestion()
        question2.header = "H2"
        question2.body = "B2"
        question2.type = "select"
        question2.marks = "10"
        question2.choices = "[]"
        question2.hasOtherOption = false

        every { results.iterator() } returns mutableListOf(question1, question2).iterator()

        val jsonArray = RealmExamQuestion.serializeQuestions(results)

        assertEquals(2, jsonArray.size())

        val q1Json = jsonArray.get(0).asJsonObject
        assertEquals("H1", q1Json.get("header").asString)
        assertEquals("B1", q1Json.get("body").asString)
        assertEquals("text", q1Json.get("type").asString)
        assertEquals("5", q1Json.get("marks").asString)
        assertTrue(q1Json.get("choices").isJsonArray)
        assertTrue(q1Json.get("hasOtherOption").asBoolean)

        val q2Json = jsonArray.get(1).asJsonObject
        assertEquals("H2", q2Json.get("header").asString)
        assertEquals("B2", q2Json.get("body").asString)
        assertEquals("select", q2Json.get("type").asString)
        assertEquals("10", q2Json.get("marks").asString)
        assertTrue(q2Json.get("choices").isJsonArray)
        assertFalse(q2Json.get("hasOtherOption").asBoolean)
    }
}
