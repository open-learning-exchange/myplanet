package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RealmExamQuestionTest {

    @MockK
    lateinit var mockRealm: Realm

    @Before
    fun setup() {
        MockKAnnotations.init(this)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testInsertExamQuestions_emptyArray() {
        val mockRealm = mockk<Realm>(relaxed = true)
        val emptyArray = JsonArray()

        RealmExamQuestion.insertExamQuestions(emptyArray, "exam123", mockRealm)

        verify(exactly = 0) { mockRealm.where(RealmExamQuestion::class.java) }
        verify(exactly = 0) { mockRealm.createObject(RealmExamQuestion::class.java, any<String>()) }
    }

    @Test
    fun testInsertExamQuestions_newQuestions() {
        val mockRealm = mockk<Realm>(relaxed = true)
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

        val mockQuery = mockk<RealmQuery<RealmExamQuestion>>(relaxed = true)

        // Return empty collection when findAll() is called on the mockQuery. No need to mock RealmResults
        @Suppress("UNCHECKED_CAST")
        val emptyResults = org.mockito.Mockito.mock(RealmResults::class.java) as RealmResults<RealmExamQuestion>
        org.mockito.Mockito.`when`(emptyResults.iterator()).thenReturn(mutableListOf<RealmExamQuestion>().iterator())
        org.mockito.Mockito.`when`(emptyResults.size).thenReturn(0)
        every { mockRealm.where(RealmExamQuestion::class.java) } returns mockQuery
        every { mockQuery.`in`("id", any<Array<String>>()) } returns mockQuery
        every { mockQuery.findAll() } returns emptyResults

        val mockQuestion = spyk(RealmExamQuestion())
        every { mockRealm.createObject(RealmExamQuestion::class.java, "q1") } returns mockQuestion

        RealmExamQuestion.insertExamQuestions(questionsArray, "exam123", mockRealm)

        verify { mockRealm.createObject(RealmExamQuestion::class.java, "q1") }
        assertEquals("exam123", mockQuestion.examId)
        assertEquals("Body 1", mockQuestion.body)
        assertEquals("select", mockQuestion.type)
        assertEquals("Header 1", mockQuestion.header)
        assertEquals("5", mockQuestion.marks)
        assertEquals(false, mockQuestion.hasOtherOption)

        // Verify correctChoice was populated
        assertNotNull(mockQuestion.getCorrectChoice())
        assertEquals(1, mockQuestion.getCorrectChoice()?.size)
        assertEquals("Choice A", mockQuestion.getCorrectChoice()?.get(0))
    }

    fun testInsertExamQuestions_emptyList() {
        val questions = JsonArray()
        RealmExamQuestion.insertExamQuestions(questions, "exam1", mockRealm)
        verify { mockRealm wasNot Called }
    }

    @Test
    fun testInsertExamQuestions_nonEmptyList() {
        val questions = JsonArray()

        // Single correct choice (string id match)
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

        // Array correct choices
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

        val mockQuery = mockk<RealmQuery<RealmExamQuestion>>()
        @Suppress("UNCHECKED_CAST")
        val mockResultsEmpty = org.mockito.Mockito.mock(RealmResults::class.java) as RealmResults<RealmExamQuestion>
        org.mockito.Mockito.`when`(mockResultsEmpty.iterator()).thenReturn(mutableListOf<RealmExamQuestion>().iterator())
        org.mockito.Mockito.`when`(mockResultsEmpty.size).thenReturn(0)

        every { mockRealm.where(RealmExamQuestion::class.java) } returns mockQuery
        every { mockQuery.`in`("id", arrayOf("q1", "q2")) } returns mockQuery
        every { mockQuery.findAll() } returns mockResultsEmpty

        val mockQ1 = RealmExamQuestion().apply { id = "q1" }
        val mockQ2 = RealmExamQuestion().apply { id = "q2" }

        every { mockRealm.createObject(RealmExamQuestion::class.java, "q1") } returns mockQ1
        every { mockRealm.createObject(RealmExamQuestion::class.java, "q2") } returns mockQ2

        RealmExamQuestion.insertExamQuestions(questions, "examId", mockRealm)

        // Asserts Q1
        assertEquals("examId", mockQ1.examId)
        assertEquals("Body1", mockQ1.body)
        assertEquals("select", mockQ1.type)
        assertEquals("Title1", mockQ1.header)
        assertEquals("1", mockQ1.marks)
        assertFalse(mockQ1.hasOtherOption)

        val correctChoiceQ1 = mockQ1.getCorrectChoice()
        assertNotNull(correctChoiceQ1)
        assertEquals(1, correctChoiceQ1?.size)
        // the implementation does NOT lowercase single mapped strings via string IDs
        assertEquals("Choice A", correctChoiceQ1?.get(0))

        assertEquals(1, mockQ1.correctChoiceArray.size())
        assertEquals("Choice A", mockQ1.correctChoiceArray.get(0).asString)

        // Asserts Q2
        assertEquals("examId", mockQ2.examId)
        assertEquals("Body2", mockQ2.body)
        assertEquals("selectMultiple", mockQ2.type)
        assertEquals("Title2", mockQ2.header)
        assertEquals("2", mockQ2.marks)
        assertTrue(mockQ2.hasOtherOption)

        val correctChoiceQ2 = mockQ2.getCorrectChoice()
        assertNotNull(correctChoiceQ2)
        assertEquals(2, correctChoiceQ2?.size)
        // setCorrectChoiceArray adds them in lowercase!
        assertEquals("choice c", correctChoiceQ2?.get(0))
        assertEquals("choice d", correctChoiceQ2?.get(1))
    }

    @Test
    fun testInsertExamQuestions_updateExisting() {
        val questions = JsonArray()
        val q1 = JsonObject().apply {
            addProperty("id", "q1")
            addProperty("body", "Updated Body")
            addProperty("type", "select")
        }
        questions.add(q1)

        val existingQuestion = RealmExamQuestion().apply {
            id = "q1"
            body = "Old Body"
        }

        val mockQuery = mockk<RealmQuery<RealmExamQuestion>>()
        val mockResults = mockk<RealmResults<RealmExamQuestion>>(relaxed = true)

        every { mockRealm.where(RealmExamQuestion::class.java) } returns mockQuery
        every { mockQuery.`in`("id", arrayOf("q1")) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        every { mockResults.iterator() } returns mutableListOf(existingQuestion).iterator()
        every { mockResults.size } returns 1

        RealmExamQuestion.insertExamQuestions(questions, "exam1", mockRealm)

        verify(exactly = 0) { mockRealm.createObject(RealmExamQuestion::class.java, any()) }

        assertEquals("exam1", existingQuestion.examId)
        assertEquals("Updated Body", existingQuestion.body)
        assertEquals("select", existingQuestion.type)
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

        val mockQuery = mockk<RealmQuery<RealmExamQuestion>>()
        @Suppress("UNCHECKED_CAST")
        val mockResultsEmpty = org.mockito.Mockito.mock(RealmResults::class.java) as RealmResults<RealmExamQuestion>
        org.mockito.Mockito.`when`(mockResultsEmpty.iterator()).thenReturn(mutableListOf<RealmExamQuestion>().iterator())
        org.mockito.Mockito.`when`(mockResultsEmpty.size).thenReturn(0)

        every { mockRealm.where(RealmExamQuestion::class.java) } returns mockQuery
        every { mockQuery.`in`("id", arrayOf("q1")) } returns mockQuery
        every { mockQuery.findAll() } returns mockResultsEmpty

        val realmQuestion = RealmExamQuestion().apply { id = "q1" }
        every { mockRealm.createObject(RealmExamQuestion::class.java, "q1") } returns realmQuestion

        RealmExamQuestion.insertExamQuestions(questions, "exam1", mockRealm)

        @Suppress("UNCHECKED_CAST")
        val mockResults = org.mockito.Mockito.mock(RealmResults::class.java) as RealmResults<RealmExamQuestion>
        org.mockito.Mockito.`when`(mockResults.iterator()).thenReturn(mutableListOf(realmQuestion).iterator())

        val serialized = RealmExamQuestion.serializeQuestions(mockResults)

        assertNotNull(serialized)
        assertEquals(1, serialized.size())

        val jsonObj = serialized.get(0).asJsonObject
        assertEquals("Title1", jsonObj.get("header").asString)
        assertEquals("Body1", jsonObj.get("body").asString)
        assertEquals("select", jsonObj.get("type").asString)
        assertEquals("1", jsonObj.get("marks").asString)
        assertFalse(jsonObj.get("hasOtherOption").asBoolean)

        val choicesArray = jsonObj.get("choices").asJsonArray
        assertEquals(1, choicesArray.size())
        val choice1 = choicesArray.get(0).asJsonObject
        assertEquals("c1", choice1.get("id").asString)
        assertEquals("Choice A", choice1.get("res").asString)

        val correctChoiceArray = jsonObj.get("correctChoice").asJsonArray
        assertEquals(1, correctChoiceArray.size())
        assertEquals("choice a", correctChoiceArray.get(0).asString)
    }

    @Test
    fun testCorrectChoiceArray_nullData() {
        val question = RealmExamQuestion()
        val jsonArray = question.correctChoiceArray
        assertNotNull(jsonArray)
        assertEquals(0, jsonArray.size())
    }
}
