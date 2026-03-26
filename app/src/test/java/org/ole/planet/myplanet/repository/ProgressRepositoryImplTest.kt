package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import io.realm.RealmQuery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.utils.DispatcherProvider

@ExperimentalCoroutinesApi
class ProgressRepositoryImplTest {

    private lateinit var repository: ProgressRepositoryImpl
    private val dispatcherProvider: DispatcherProvider = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val databaseService: DatabaseService = mockk(relaxed = true)

    @Before
    fun setUp() {
        every { dispatcherProvider.io } returns testDispatcher
        repository = spyk(ProgressRepositoryImpl(
            databaseService,
            UnconfinedTestDispatcher(),
            dispatcherProvider
        ), recordPrivateCalls = true)
        coEvery { repository["queryList"](RealmMyCourse::class.java, any<Function1<*, *>>()) } returns emptyList<RealmMyCourse>()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun fetchCourseData_uses_dispatcherProvider_io() = runTest(testDispatcher) {
        val result = repository.fetchCourseData("user123")
        assertEquals(JsonArray(), result)
        verify { dispatcherProvider.io }
    }

    @Test
    fun testGetCurrentProgress_EmptyProgress() = testScope.runTest {
        val steps = listOf(
            RealmCourseStep().apply { id = "step1" },
            RealmCourseStep().apply { id = "step2" }
        )

        coEvery {
            repository["queryList"](RealmCourseProgress::class.java, any<Function1<RealmQuery<RealmCourseProgress>, Unit>>())
        } returns emptyList<RealmCourseProgress>()

        val progress = repository.getCurrentProgress(steps, "user1", "course1")
        advanceUntilIdle()
        assertEquals(0, progress)
    }

    @Test
    fun testGetCurrentProgress_GapsInSteps() = testScope.runTest {
        val steps = listOf(
            RealmCourseStep().apply { id = "step1" },
            RealmCourseStep().apply { id = "step2" },
            RealmCourseStep().apply { id = "step3" },
            RealmCourseStep().apply { id = "step4" }
        )

        // Steps 1 and 3 are present, 2 is missing. The gap is at step 2, so progress should be 1.
        val progresses = listOf(
            RealmCourseProgress().apply { stepNum = 1 },
            RealmCourseProgress().apply { stepNum = 3 }
        )

        coEvery {
            repository["queryList"](RealmCourseProgress::class.java, any<Function1<RealmQuery<RealmCourseProgress>, Unit>>())
        } returns progresses

        var progress = repository.getCurrentProgress(steps, "user1", "course1")
        advanceUntilIdle()
        assertEquals(1, progress)

        // Now test where 1, 2, 3 are present, 4 is missing, to ensure it doesn't just always return 1
        val progresses2 = listOf(
            RealmCourseProgress().apply { stepNum = 1 },
            RealmCourseProgress().apply { stepNum = 2 },
            RealmCourseProgress().apply { stepNum = 3 }
        )

        coEvery {
            repository["queryList"](RealmCourseProgress::class.java, any<Function1<RealmQuery<RealmCourseProgress>, Unit>>())
        } returns progresses2

        progress = repository.getCurrentProgress(steps, "user1", "course1")
        advanceUntilIdle()
        assertEquals(3, progress)
    }

    @Test
    fun testGetCurrentProgress_FullyCompleted() = testScope.runTest {
        val steps = listOf(
            RealmCourseStep().apply { id = "step1" },
            RealmCourseStep().apply { id = "step2" }
        )

        val progresses = listOf(
            RealmCourseProgress().apply { stepNum = 1 },
            RealmCourseProgress().apply { stepNum = 2 }
        )

        coEvery {
            repository["queryList"](RealmCourseProgress::class.java, any<Function1<RealmQuery<RealmCourseProgress>, Unit>>())
        } returns progresses

        val progress = repository.getCurrentProgress(steps, "user1", "course1")
        advanceUntilIdle()
        assertEquals(2, progress)
    }

    @Test
    fun testFetchCourseData_PopulatesFieldsCorrectly() = testScope.runTest {
        val myCourses = listOf(
            RealmMyCourse().apply {
                courseId = "course1"
                courseTitle = "Test Course"
            }
        )

        val steps = listOf(
            RealmCourseStep().apply { courseId = "course1" }
        )
        myCourses[0].courseSteps = io.realm.RealmList(*steps.toTypedArray())

        val exams = listOf(
            RealmStepExam().apply {
                id = "exam1"
                courseId = "course1"
            }
        )

        val submissions = listOf(
            RealmSubmission().apply {
                id = "sub1"
                userId = "user1"
                parentId = "course1"
                type = "exam"
            }
        )

        val answers = listOf(
            RealmAnswer().apply {
                submissionId = "sub1"
                questionId = "q1"
                mistakes = 2
            }
        )

        val question = RealmExamQuestion().apply {
            id = "q1"
            examId = "exam1"
        }

        coEvery {
            repository["queryList"](RealmMyCourse::class.java, any<Function1<RealmQuery<RealmMyCourse>, Unit>>())
        } returns myCourses

        coEvery {
            repository["queryList"](RealmCourseProgress::class.java, any<Function1<RealmQuery<RealmCourseProgress>, Unit>>())
        } returns listOf(RealmCourseProgress().apply { stepNum = 1 })

        coEvery {
            repository["queryList"](RealmSubmission::class.java, any<Function1<RealmQuery<RealmSubmission>, Unit>>())
        } returns submissions

        coEvery {
            repository["queryList"](RealmStepExam::class.java, any<Function1<RealmQuery<RealmStepExam>, Unit>>())
        } returns exams

        coEvery {
            repository["queryList"](RealmAnswer::class.java, any<Function1<RealmQuery<RealmAnswer>, Unit>>())
        } returns answers

        coEvery {
            repository["findByField"](RealmExamQuestion::class.java, "id", "q1", false)
        } returns question

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
