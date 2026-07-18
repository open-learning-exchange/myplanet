package org.ole.planet.myplanet.model
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.ExamQuestion

import android.text.TextUtils
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class StepExamTest {

    @Before
    fun setup() {
        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any()) } answers {
            val str = arg<CharSequence?>(0)
            str.isNullOrEmpty()
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

        val result = StepExam.insertCourseStepsExams("course1", "step1", examJson)

        assertEquals(examId, result.id)
        assertEquals("Test Exam", result.name)
        assertEquals("Exam Description", result.description)
        assertEquals("exam", result.type)
        assertEquals("course1", result.courseId)
        assertEquals("step1", result.stepId)
        assertEquals(false, result.isFromNation)
        assertEquals("team1", result.teamId)
        assertEquals(true, result.isTeamShareAllowed)
        assertEquals("survey1", result.sourceSurveyId)
        assertEquals(1, result.noOfQuestions)
    }

    @Test
    fun testInsertCourseStepsExams_withParentId() {
        val examId = UUID.randomUUID().toString()
        val examJson = JsonObject().apply {
            addProperty("_id", examId)
            addProperty("name", "Nation Exam")
            add("questions", JsonArray())
        }

        val result = StepExam.insertCourseStepsExams("course1", "step1", examJson, "parent-nation-id")

        assertEquals(examId, result.id)
        assertEquals("Nation Exam", result.name)
        assertTrue(result.isFromNation)
        assertEquals(0, result.noOfQuestions)
    }

    @Test
    fun testSerializeExam() {
        val exam = StepExam().apply {
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

        val questions = listOf(
            ExamQuestion().apply {
                header = "Header"
                body = "Body"
                type = "select"
                marks = "1"
                setCorrectChoices(listOf("Choice A"))
            }
        )
        val json = StepExam.serializeExam(exam, questions)

        assertEquals("exam1", json.get("_id").asString)
        assertEquals("Test Exam", json.get("name").asString)
        assertEquals("Description", json.get("description").asString)
        assertEquals("exam", json.get("type").asString)
        assertEquals("survey1", json.get("sourceSurveyId").asString)
        assertEquals("team1", json.get("teamId").asString)
        assertTrue(json.has("questions"))
    }
}
