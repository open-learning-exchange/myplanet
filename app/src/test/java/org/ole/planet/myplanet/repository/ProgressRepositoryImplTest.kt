package org.ole.planet.myplanet.repository

import android.content.Context
import com.google.gson.JsonObject
import io.realm.RealmQuery
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.every
import org.junit.After
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission

@ExperimentalCoroutinesApi
class ProgressRepositoryImplTest {

    private lateinit var repository: ProgressRepositoryImpl
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private var mockProgresses: List<RealmCourseProgress> = emptyList()
    private var mockMyCourses: List<RealmMyCourse> = emptyList()
    private var mockExams: List<RealmStepExam> = emptyList()
    private var mockSubmissions: List<RealmSubmission> = emptyList()
    private var mockAnswers: List<RealmAnswer> = emptyList()
    private var mockQuestions: Map<String, RealmExamQuestion> = emptyMap()

    @Before
    fun setUp() {
        mockkStatic(android.os.SystemClock::class)
        every { android.os.SystemClock.sleep(any()) } returns Unit
        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.filesDir } returns java.io.File(System.getProperty("java.io.tmpdir"))

        val databaseService = mockk<DatabaseService>(relaxed = true)
        every { databaseService.ioDispatcher } returns testDispatcher

        repository = object : ProgressRepositoryImpl(databaseService) {
            override suspend fun <T : io.realm.RealmObject> queryList(
                clazz: Class<T>,
                builder: RealmQuery<T>.() -> Unit
            ): List<T> {
                return when (clazz) {
                    RealmCourseProgress::class.java -> mockProgresses as List<T>
                    RealmMyCourse::class.java -> mockMyCourses as List<T>
                    RealmStepExam::class.java -> mockExams as List<T>
                    RealmSubmission::class.java -> mockSubmissions as List<T>
                    RealmAnswer::class.java -> mockAnswers as List<T>
                    else -> emptyList()
                }
            }

            override suspend fun <T : io.realm.RealmObject, V : Any> findByField(
                clazz: Class<T>,
                fieldName: String,
                value: V
            ): T? {
                if (clazz == RealmExamQuestion::class.java && fieldName == "id") {
                    return mockQuestions[value as String] as T?
                }
                return null
            }
        }
    }

    @After
    fun tearDown() {
        io.mockk.unmockkAll()
    }

    @Test
    fun testGetCurrentProgress_EmptyProgress() = testScope.runTest {
        val steps = listOf(
            RealmCourseStep().apply { id = "step1" },
            RealmCourseStep().apply { id = "step2" }
        )

        mockProgresses = emptyList()

        val progress = repository.getCurrentProgress(steps, "user1", "course1")
        advanceUntilIdle()
        assertEquals(0, progress)
    }

    @Test
    fun testGetCurrentProgress_GapsInSteps() = testScope.runTest {
        val steps = listOf(
            RealmCourseStep().apply { id = "step1" },
            RealmCourseStep().apply { id = "step2" },
            RealmCourseStep().apply { id = "step3" }
        )

        mockProgresses = listOf(
            RealmCourseProgress().apply { stepNum = 1 },
            RealmCourseProgress().apply { stepNum = 3 }
        )

        val progress = repository.getCurrentProgress(steps, "user1", "course1")
        advanceUntilIdle()
        assertEquals(1, progress)
    }

    @Test
    fun testGetCurrentProgress_FullyCompleted() = testScope.runTest {
        val steps = listOf(
            RealmCourseStep().apply { id = "step1" },
            RealmCourseStep().apply { id = "step2" }
        )

        mockProgresses = listOf(
            RealmCourseProgress().apply { stepNum = 1 },
            RealmCourseProgress().apply { stepNum = 2 }
        )

        val progress = repository.getCurrentProgress(steps, "user1", "course1")
        advanceUntilIdle()
        assertEquals(2, progress)
    }

    @Test
    fun testFetchCourseData_PopulatesFieldsCorrectly() = testScope.runTest {
        val steps = listOf(
            RealmCourseStep().apply { courseId = "course1" }
        )

        mockMyCourses = listOf(
            RealmMyCourse().apply {
                courseId = "course1"
                courseTitle = "Test Course"
                courseSteps = io.realm.RealmList(*steps.toTypedArray())
            }
        )

        mockExams = listOf(
            RealmStepExam().apply {
                id = "exam1"
                courseId = "course1"
            }
        )

        mockSubmissions = listOf(
            RealmSubmission().apply {
                id = "sub1"
                userId = "user1"
                parentId = "course1"
                type = "exam"
            }
        )

        mockAnswers = listOf(
            RealmAnswer().apply {
                submissionId = "sub1"
                questionId = "q1"
                mistakes = 2
            }
        )

        mockQuestions = mapOf(
            "q1" to RealmExamQuestion().apply {
                id = "q1"
                examId = "exam1"
            }
        )

        mockProgresses = listOf(RealmCourseProgress().apply { stepNum = 1 })

        val data = repository.fetchCourseData("user1")
        advanceUntilIdle()

        assertEquals(1, data.size())
        val obj = data[0].asJsonObject

        assertEquals("Test Course", obj.get("courseName").asString)
        assertEquals("course1", obj.get("courseId").asString)

        val progress = obj.get("progress").asJsonObject
        assertEquals(1, progress.get("max").asInt)
        assertEquals(1, progress.get("current").asInt)

        assertEquals(2, obj.get("mistakes").asInt)

        val stepMistake = obj.get("stepMistake").asJsonObject
        assertEquals(2, stepMistake.get("0").asInt) // Exam 1 is at index 0
    }
}
