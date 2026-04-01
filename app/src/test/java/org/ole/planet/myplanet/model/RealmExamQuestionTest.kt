package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmQuery
import io.realm.RealmResults
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.utils.JsonUtils

class RealmExamQuestionTest {

    @MockK
    lateinit var mockRealm: Realm

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(JsonUtils::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testGetCorrectChoice() {
        val question = RealmExamQuestion()
        val choices = RealmList<String>("A", "B")

        // Use reflection since correctChoice is private
        val field = RealmExamQuestion::class.java.getDeclaredField("correctChoice")
        field.isAccessible = true
        field.set(question, choices)

        val result = question.getCorrectChoice()
        assertNotNull(result)
        assertEquals(2, result?.size)
        assertEquals("A", result?.get(0))
        assertEquals("B", result?.get(1))
    }

    @Test
    fun testCorrectChoiceArray_withData() {
        val question = RealmExamQuestion()
        val choices = RealmList<String>("A", "B")

        val field = RealmExamQuestion::class.java.getDeclaredField("correctChoice")
        field.isAccessible = true
        field.set(question, choices)

        val jsonArray = question.correctChoiceArray
        assertNotNull(jsonArray)
        assertEquals(2, jsonArray.size())
        assertEquals("A", jsonArray.get(0).asString)
        assertEquals("B", jsonArray.get(1).asString)
    }

    @Test
    fun testCorrectChoiceArray_nullData() {
        val question = RealmExamQuestion()
        val jsonArray = question.correctChoiceArray
        assertNotNull(jsonArray)
        assertEquals(0, jsonArray.size())
    }

    @Test
    fun testSerializeQuestions() {
        // Mock RealmResults
        val mockResults = mockk<RealmResults<RealmExamQuestion>>()
        val question1 = RealmExamQuestion().apply {
            header = "Header1"
            body = "Body1"
            type = "Type1"
            marks = "10"
            choices = "[\"A\",\"B\"]"
            hasOtherOption = true
        }
        val choices = RealmList<String>("A")
        val field = RealmExamQuestion::class.java.getDeclaredField("correctChoice")
        field.isAccessible = true
        field.set(question1, choices)

        val iterator = mutableListOf(question1).iterator()
        every { mockResults.iterator() } returns iterator

        every { JsonUtils.getStringAsJsonArray("[\"A\",\"B\"]") } returns JsonArray().apply {
            add("A")
            add("B")
        }

        val jsonArray = RealmExamQuestion.serializeQuestions(mockResults)

        assertNotNull(jsonArray)
        assertEquals(1, jsonArray.size())
        val jsonObj = jsonArray.get(0).asJsonObject
        assertEquals("Header1", jsonObj.get("header").asString)
        assertEquals("Body1", jsonObj.get("body").asString)
        assertEquals("Type1", jsonObj.get("type").asString)
        assertEquals("10", jsonObj.get("marks").asString)
        assertTrue(jsonObj.get("hasOtherOption").asBoolean)

        val choicesArray = jsonObj.get("choices").asJsonArray
        assertEquals(2, choicesArray.size())
        assertEquals("A", choicesArray.get(0).asString)
        assertEquals("B", choicesArray.get(1).asString)

        val correctChoiceArray = jsonObj.get("correctChoice").asJsonArray
        assertEquals(1, correctChoiceArray.size())
        assertEquals("A", correctChoiceArray.get(0).asString)
    }

    @Test
    fun testInsertExamQuestions_emptyList() {
        val questions = JsonArray()
        RealmExamQuestion.insertExamQuestions(questions, "exam1", mockRealm)
        // Verify no interactions with realm
        verify { mockRealm wasNot Called }
    }
}
