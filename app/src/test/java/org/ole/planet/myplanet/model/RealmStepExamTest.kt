package org.ole.planet.myplanet.model

import android.text.TextUtils
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.utils.JsonUtils

class RealmStepExamTest {

    @MockK
    lateinit var mockRealm: Realm

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockkObject(RealmExamQuestion.Companion)
        mockkStatic(JsonUtils::class)
        mockkStatic(TextUtils::class)

        every { TextUtils.isEmpty(any()) } answers {
            val str = arg<CharSequence?>(0)
            str.isNullOrEmpty()
        }

        every { JsonUtils.getString(any(), any<JsonObject>()) } answers {
            val key = arg<String>(0)
            val obj = arg<JsonObject>(1)
            if (obj.has(key) && !obj.get(key).isJsonNull) obj.get(key).asString else ""
        }
        every { JsonUtils.getLong(any(), any<JsonObject>()) } answers {
            val key = arg<String>(0)
            val obj = arg<JsonObject>(1)
            if (obj.has(key) && !obj.get(key).isJsonNull) obj.get(key).asLong else 0L
        }
        every { JsonUtils.getInt(any(), any<JsonObject>()) } answers {
            val key = arg<String>(0)
            val obj = arg<JsonObject>(1)
            if (obj.has(key) && !obj.get(key).isJsonNull) obj.get(key).asInt else 0
        }
        every { JsonUtils.getBoolean(any(), any<JsonObject>()) } answers {
            val key = arg<String>(0)
            val obj = arg<JsonObject>(1)
            if (obj.has(key) && !obj.get(key).isJsonNull) obj.get(key).asBoolean else false
        }
        every { JsonUtils.getJsonArray(any(), any<JsonObject>()) } answers {
            val key = arg<String>(0)
            val obj = arg<JsonObject>(1)
            if (obj.has(key) && obj.get(key).isJsonArray) obj.get(key).asJsonArray else JsonArray()
        }

        every { mockRealm.isInTransaction } returns false
        every { mockRealm.executeTransaction(any()) } answers {
            val transaction = arg<Realm.Transaction>(0)
            try {
                transaction.execute(mockRealm)
            } catch (e: Exception) {
                // Ignore for mock
            }
        }
        every { mockRealm.executeTransactionAsync(any(), any(), any()) } answers {
            val transaction = arg<Realm.Transaction>(0)
            try {
                transaction.execute(mockRealm)
            } catch (e: Exception) {
            }
            val success = arg<Realm.Transaction.OnSuccess>(1)
            success.onSuccess()
            mockk()
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testInsertCourseStepsExams() {
        val examId = UUID.randomUUID().toString()
        val examJson = JsonObject().apply {
            addProperty("_id", examId)
            addProperty("name", "Test Exam")
            addProperty("description", "Exam Description")
            addProperty("passingPercentage", "50")
            addProperty("type", "exam")
            addProperty("_rev", "1-abc")
            addProperty("createdBy", "admin")
            addProperty("sourcePlanet", "earth")
            addProperty("createdDate", 1620000000000L)
            addProperty("updatedDate", 1620000000000L)
            addProperty("adoptionDate", 1620000000000L)
            addProperty("totalMarks", 100)
            addProperty("teamId", "team1")
            addProperty("teamShareAllowed", true)
            addProperty("sourceSurveyId", "survey1")
            add("questions", JsonArray().apply { add(JsonObject()) })
        }

        val mockQuery = mockk<RealmQuery<RealmStepExam>>(relaxed = true)
        every { mockRealm.where(RealmStepExam::class.java) } returns mockQuery
        every { mockQuery.equalTo("id", examId) } returns mockQuery
        every { mockQuery.findFirst() } returns null

        val mockRealmStepExam = mockk<RealmStepExam>(relaxed = true)
        every { mockRealm.createObject(RealmStepExam::class.java, "") } returns mockRealmStepExam
        every { mockRealm.createObject(RealmStepExam::class.java, examId) } returns mockRealmStepExam
        every { mockRealm.createObject(RealmStepExam::class.java, null as String?) } returns mockRealmStepExam
        every { mockRealm.createObject(RealmStepExam::class.java, any<String>()) } returns mockRealmStepExam

        val mockQuestionsQuery = mockk<RealmQuery<RealmExamQuestion>>(relaxed = true)
        val mockQuestionsResults = mockk<RealmResults<RealmExamQuestion>>(relaxed = true)
        every { mockRealm.where(RealmExamQuestion::class.java) } returns mockQuestionsQuery
        every { mockQuestionsQuery.equalTo("examId", examId) } returns mockQuestionsQuery
        every { mockQuestionsQuery.findAll() } returns mockQuestionsResults
        every { mockQuestionsResults.isEmpty() } returns true

        every { RealmExamQuestion.insertExamQuestions(any(), any(), any()) } just Runs

        // Calling the 4 arg which delegates to 5 arg with empty string
        RealmStepExam.insertCourseStepsExams("course1", "step1", examJson, mockRealm)

        verify { mockRealmStepExam.name = "Test Exam" }
        verify { mockRealmStepExam.description = "Exam Description" }
        verify { mockRealmStepExam.type = "exam" }
        verify { mockRealmStepExam.courseId = "course1" }
        verify { mockRealmStepExam.stepId = "step1" }
        verify { mockRealmStepExam.isFromNation = false }
        verify { mockRealmStepExam.teamId = "team1" }
        verify { mockRealmStepExam.isTeamShareAllowed = true }
        verify { mockRealmStepExam.sourceSurveyId = "survey1" }
        verify { mockRealmStepExam.noOfQuestions = 1 }
    }

    @Test
    fun testInsertCourseStepsExams_withParentId() {
        val examId = UUID.randomUUID().toString()
        val examJson = JsonObject().apply {
            addProperty("_id", examId)
            addProperty("name", "Nation Exam")
            add("questions", JsonArray())
        }

        val mockQuery = mockk<RealmQuery<RealmStepExam>>(relaxed = true)
        every { mockRealm.where(RealmStepExam::class.java) } returns mockQuery
        every { mockQuery.equalTo("id", examId) } returns mockQuery
        every { mockQuery.findFirst() } returns null

        val mockRealmStepExam = mockk<RealmStepExam>(relaxed = true)
        val parentId = "parent-nation-id"
        // Ensure ambiguity is removed by explicitly testing specific arg logic
        every { mockRealm.createObject(RealmStepExam::class.java, parentId) } returns mockRealmStepExam
        every { mockRealm.createObject(RealmStepExam::class.java, examId) } returns mockRealmStepExam
        every { mockRealm.createObject(RealmStepExam::class.java, "") } returns mockRealmStepExam
        every { mockRealm.createObject(RealmStepExam::class.java, any<String>()) } returns mockRealmStepExam

        val mockQuestionsQuery = mockk<RealmQuery<RealmExamQuestion>>(relaxed = true)
        val mockQuestionsResults = mockk<RealmResults<RealmExamQuestion>>(relaxed = true)
        every { mockRealm.where(RealmExamQuestion::class.java) } returns mockQuestionsQuery
        every { mockQuestionsQuery.equalTo("examId", examId) } returns mockQuestionsQuery
        every { mockQuestionsQuery.findAll() } returns mockQuestionsResults
        every { mockQuestionsResults.isEmpty() } returns true

        every { RealmExamQuestion.insertExamQuestions(any(), any(), any()) } just Runs

        // Explicitly calling the 5 arg method
        RealmStepExam.insertCourseStepsExams("course1", "step1", examJson, parentId, mockRealm)

        verify { mockRealmStepExam.name = "Nation Exam" }
        verify { mockRealmStepExam.isFromNation = true }
        verify { mockRealmStepExam.noOfQuestions = 0 }
    }


    @Test
    fun testSerializeExam() {
        val exam = RealmStepExam().apply {
            id = "exam1"
            _rev = "1-abc"
            name = "Test Exam"
            description = "Description"
            passingPercentage = "50"
            type = "exam"
            updatedDate = 1620000000000L
            createdDate = 1620000000000L
            adoptionDate = 1620000000000L
            sourcePlanet = "earth"
            totalMarks = 100
            createdBy = "admin"
            sourceSurveyId = "survey1"
            teamId = "team1"
        }

        val mockQuery = mockk<RealmQuery<RealmExamQuestion>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmExamQuestion>>(relaxed = true)
        every { mockRealm.where(RealmExamQuestion::class.java) } returns mockQuery
        every { mockQuery.equalTo("examId", "exam1") } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        every { mockResults.iterator() } returns mutableListOf<RealmExamQuestion>().iterator()
        every { mockResults.isLoaded } returns true
        every { mockResults.isValid } returns true

        every { RealmExamQuestion.serializeQuestions(any()) } returns JsonArray()

        val questions = listOf(mockk<RealmExamQuestion>())
        val json = RealmStepExam.serializeExam(exam, questions)

        assertEquals("exam1", json.get("_id").asString)
        assertEquals("Test Exam", json.get("name").asString)
        assertEquals("Description", json.get("description").asString)
        assertEquals("exam", json.get("type").asString)
        assertEquals("survey1", json.get("sourceSurveyId").asString)
        assertEquals("team1", json.get("teamId").asString)
        assertTrue(json.has("questions"))
    }

    @Test
    fun testGetIds() {
        val exam1 = RealmStepExam().apply { id = "exam1"; courseId = "course1"; type = "exam" }
        val exam2 = RealmStepExam().apply { id = "survey1"; type = "survey" }

        val ids = RealmStepExam.getIds(listOf(exam1, exam2))

        assertEquals(2, ids.size)
        assertEquals("exam1@course1", ids[0])
        assertEquals("survey1", ids[1])
    }

    @Test
    fun testGetSurveyCreationTime() {
        val surveyId = "survey1"
        val expectedDate = 1620000000000L
        val mockSurvey = mockk<RealmStepExam>(relaxed = true)
        every { mockSurvey.createdDate } returns expectedDate

        val mockQuery = mockk<RealmQuery<RealmStepExam>>(relaxed = true)
        every { mockRealm.where(RealmStepExam::class.java) } returns mockQuery
        every { mockQuery.equalTo("id", surveyId) } returns mockQuery
        every { mockQuery.findFirst() } returns mockSurvey

        val time = RealmStepExam.getSurveyCreationTime(surveyId, mockRealm)
        assertEquals(expectedDate, time)
    }
}