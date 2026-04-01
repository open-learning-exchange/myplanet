package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmQuery
import io.realm.RealmResults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.utils.JsonUtils

class RealmExamQuestionTest {

    @Before
    fun setup() {
        mockkStatic(JsonUtils::class)
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
        val mockResults = mockk<RealmResults<RealmExamQuestion>>(relaxed = true)

        every { mockRealm.where(RealmExamQuestion::class.java) } returns mockQuery
        every { mockQuery.`in`("id", any<Array<String>>()) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        every { mockResults.iterator() } returns mutableListOf<RealmExamQuestion>().iterator()

        val mockQuestion = mockk<RealmExamQuestion>(relaxed = true)
        every { mockRealm.createObject(RealmExamQuestion::class.java, "q1") } returns mockQuestion

        RealmExamQuestion.insertExamQuestions(questionsArray, "exam123", mockRealm)

        verify { mockRealm.createObject(RealmExamQuestion::class.java, "q1") }
        verify { mockQuestion.examId = "exam123" }
        verify { mockQuestion.body = "Body 1" }
        verify { mockQuestion.type = "select" }
        verify { mockQuestion.header = "Header 1" }
        verify { mockQuestion.marks = "5" }
        verify { mockQuestion.hasOtherOption = false }
    }

    @Test
    fun testSerializeQuestions() {
        val mockResults = mockk<RealmResults<RealmExamQuestion>>(relaxed = true)
        val mockQuestion = mockk<RealmExamQuestion>(relaxed = true)

        every { mockQuestion.header } returns "Header 1"
        every { mockQuestion.body } returns "Body 1"
        every { mockQuestion.type } returns "select"
        every { mockQuestion.marks } returns "5"
        every { mockQuestion.choices } returns "[{\"res\":\"Choice A\",\"id\":\"c1\"}]"
        every { mockQuestion.hasOtherOption } returns false
        every { mockQuestion.correctChoiceArray } returns JsonArray()

        every { mockResults.iterator() } returns mutableListOf(mockQuestion).iterator()

        val result = RealmExamQuestion.serializeQuestions(mockResults)

        assertEquals(1, result.size())
        val question = result[0].asJsonObject
        assertEquals("Header 1", question["header"].asString)
        assertEquals("Body 1", question["body"].asString)
        assertEquals("select", question["type"].asString)
        assertEquals("5", question["marks"].asString)
        assertEquals(false, question["hasOtherOption"].asBoolean)
    }
}
