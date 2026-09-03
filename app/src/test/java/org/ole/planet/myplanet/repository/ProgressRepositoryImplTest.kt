package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.AnswerDao
import org.ole.planet.myplanet.data.room.dao.CourseProgressDao
import org.ole.planet.myplanet.data.room.dao.CourseStepDao
import org.ole.planet.myplanet.data.room.dao.ExamDao
import org.ole.planet.myplanet.data.room.dao.QuestionDao
import org.ole.planet.myplanet.data.room.dao.SubmissionDao
import org.ole.planet.myplanet.model.Answer
import org.ole.planet.myplanet.model.CourseProgress
import org.ole.planet.myplanet.model.CourseStep
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.utils.DispatcherProvider

@ExperimentalCoroutinesApi
class ProgressRepositoryImplTest {

    private lateinit var repository: ProgressRepositoryImpl
    private val testDispatcher = StandardTestDispatcher()
    private val dispatcherProvider: DispatcherProvider = org.ole.planet.myplanet.utils.TestDispatcherProvider(testDispatcher)
    private val testScope = TestScope(testDispatcher)
    private lateinit var mockCoursesRepository: CoursesRepository
    private val courseProgressDao: CourseProgressDao = mockk(relaxed = true)
    private val courseStepDao: CourseStepDao = mockk(relaxed = true)
    private val examDao: ExamDao = mockk(relaxed = true)
    private val submissionDao: SubmissionDao = mockk(relaxed = true)
    private val answerDao: AnswerDao = mockk(relaxed = true)
    private val questionDao: QuestionDao = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockCoursesRepository = mockk<CoursesRepository>()
        coEvery { mockCoursesRepository.getMyCourses(any()) } returns emptyList()
        repository = spyk(
            ProgressRepositoryImpl(
                dispatcherProvider,
                dagger.Lazy { mockCoursesRepository },
                dagger.Lazy { mockk(relaxed = true) },
                courseProgressDao,
                courseStepDao,
                examDao,
                submissionDao,
                answerDao,
                questionDao
            ),
            recordPrivateCalls = true
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun fetchCourseData_executes_successfully() = runTest(testDispatcher) {
        coEvery { submissionDao.getExamSubmissionsByUser("user123") } returns emptyList()
        val result = repository.fetchCourseData("user123")
        assertEquals(JsonArray(), result)
    }

    @Test
    fun testGetCurrentProgress_EmptyProgress() = testScope.runTest {
        val steps = listOf(
            CourseStep().apply { id = "step1" },
            CourseStep().apply { id = "step2" }
        )

        coEvery { courseProgressDao.getByUserAndCourse("user1", "course1") } returns emptyList()

        val progress = repository.getCurrentProgress(steps, "user1", "course1")
        advanceUntilIdle()
        assertEquals(0, progress)
    }

    @Test
    fun testGetCurrentProgress_GapsInSteps() = testScope.runTest {
        val steps = listOf(
            CourseStep().apply { id = "step1" },
            CourseStep().apply { id = "step2" },
            CourseStep().apply { id = "step3" },
            CourseStep().apply { id = "step4" }
        )

        val progresses = listOf(
            CourseProgress().apply { stepNum = 1 },
            CourseProgress().apply { stepNum = 3 }
        )

        coEvery { courseProgressDao.getByUserAndCourse("user1", "course1") } returns progresses

        var progress = repository.getCurrentProgress(steps, "user1", "course1")
        advanceUntilIdle()
        assertEquals(1, progress)

        val progresses2 = listOf(
            CourseProgress().apply { stepNum = 1 },
            CourseProgress().apply { stepNum = 2 },
            CourseProgress().apply { stepNum = 3 }
        )

        coEvery { courseProgressDao.getByUserAndCourse("user1", "course1") } returns progresses2

        progress = repository.getCurrentProgress(steps, "user1", "course1")
        advanceUntilIdle()
        assertEquals(3, progress)
    }

    @Test
    fun testGetCurrentProgress_FullyCompleted() = testScope.runTest {
        val steps = listOf(
            CourseStep().apply { id = "step1" },
            CourseStep().apply { id = "step2" }
        )

        val progresses = listOf(
            CourseProgress().apply { stepNum = 1 },
            CourseProgress().apply { stepNum = 2 }
        )

        coEvery { courseProgressDao.getByUserAndCourse("user1", "course1") } returns progresses

        val progress = repository.getCurrentProgress(steps, "user1", "course1")
        advanceUntilIdle()
        assertEquals(2, progress)
    }

    @Test
    fun testFetchCourseData_PopulatesFieldsCorrectly() = testScope.runTest {
        val myCourses = listOf(
            MyCourse().apply {
                courseId = "course1"
                courseTitle = "Test Course"
            }
        )

        val steps = listOf(
            CourseStep().apply { courseId = "course1" }
        )
        myCourses[0].courseSteps = steps.toMutableList()

        val exams = listOf(
            StepExam().apply {
                id = "exam1"
                courseId = "course1"
            }
        )

        val submissions = listOf(
            Submission().apply {
                id = "sub1"
                userId = "user1"
                parentId = "course1"
                type = "exam"
            }
        )

        val answers = listOf(
            Answer().apply {
                submissionId = "sub1"
                questionId = "q1"
                mistakes = 2
            }
        )

        val question = ExamQuestion().apply {
            id = "q1"
            examId = "exam1"
        }

        coEvery { mockCoursesRepository.getMyCourses(any()) } returns myCourses

        coEvery { courseStepDao.getByCourseIds(listOf("course1")) } returns steps.mapIndexed { index, step ->
            CourseStep(id = step.id ?: "step$index", courseId = step.courseId)
        }

        coEvery { courseProgressDao.getByUserAndCourseIds("user1", listOf("course1")) } returns listOf(CourseProgress().apply {
            stepNum = 1
            courseId = "course1"
        })

        coEvery { submissionDao.getExamSubmissionsByUser("user1") } returns submissions.map { submission ->
            Submission(id = submission.id ?: "submission", parentId = submission.parentId, userId = submission.userId, type = submission.type)
        }

        coEvery { examDao.getByCourseIds(listOf("course1")) } returns exams.map { exam ->
            StepExam(id = exam.id ?: "exam", courseId = exam.courseId, stepId = exam.stepId, type = exam.type)
        }

        coEvery { answerDao.getBySubmissionIds(listOf("sub1")) } returns answers.map { answer ->
            org.ole.planet.myplanet.model.Answer(
                id = answer.id ?: "answer",
                questionId = answer.questionId,
                submissionId = answer.submissionId,
                mistakes = answer.mistakes,
            )
        }

        coEvery { questionDao.getByIds(listOf("q1")) } returns listOf(
            org.ole.planet.myplanet.model.ExamQuestion(id = question.id ?: "q1", examId = question.examId)
        )

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
        assertEquals(2, stepMistake.get("0").asInt)
    }

    @Test
    fun testGetCourseProgress() = testScope.runTest {
        val courseIds = listOf("course1", "course2")
        val steps1 = listOf(CourseStep().apply { courseId = "course1" })
        val steps2 = listOf(CourseStep().apply { courseId = "course2" }, CourseStep().apply { courseId = "course2" })

        val progresses1 = listOf(CourseProgress().apply { courseId = "course1"; stepNum = 1 })

        coEvery { courseStepDao.getByCourseIds(courseIds) } returns (steps1 + steps2).mapIndexed { index, step ->
            CourseStep(id = step.id ?: "step$index", courseId = step.courseId)
        }

        coEvery { courseProgressDao.getByUserAndCourseIds("user1", courseIds) } returns progresses1

        val result = repository.getCourseProgress(courseIds, "user1")
        advanceUntilIdle()

        assertEquals(2, result.size)
        assertEquals(1, result["course1"]?.max)
        assertEquals(1, result["course1"]?.current)

        assertEquals(2, result["course2"]?.max)
        assertEquals(0, result["course2"]?.current)
    }

    @Test
    fun testGetProgressRecords() = testScope.runTest {
        val progresses = listOf(
            CourseProgress().apply { userId = "user1"; courseId = "course1" },
            CourseProgress().apply { userId = "user1"; courseId = "course2" }
        )

        coEvery { courseProgressDao.getByUser("user1") } returns progresses

        val result = repository.getProgressRecords("user1")
        advanceUntilIdle()

        assertEquals(2, result.size)
        assertEquals("course1", result[0].courseId)
    }

    @Test
    fun testGetCompletedCourses() = testScope.runTest {
        val myCourses = listOf(
            MyCourse().apply {
                courseId = "course1"
                courseTitle = "Course 1"
                courseSteps = mutableListOf(CourseStep().apply { courseId = "course1" })
            },
            MyCourse().apply {
                courseId = "course2"
                courseTitle = "Course 2"
                courseSteps = mutableListOf(
                    CourseStep().apply { courseId = "course2" },
                    CourseStep().apply { courseId = "course2" }
                )
            }
        )

        val progresses = listOf(
            CourseProgress().apply { courseId = "course1"; stepNum = 1; passed = true },
            CourseProgress().apply { courseId = "course2"; stepNum = 1; passed = true }
        )

        coEvery { mockCoursesRepository.getMyCourses("user1") } returns myCourses
        coEvery { courseProgressDao.getByUser("user1") } returns progresses

        val result = repository.getCompletedCourses("user1")
        advanceUntilIdle()

        assertEquals(1, result.size)
        assertEquals("course1", result[0].courseId)
        assertEquals("Course 1", result[0].courseTitle)
    }

    @Test
    fun testHasUserCompletedSync() = testScope.runTest {
        val activitiesRepo = mockk<ActivitiesRepository>()
        val localRepository = ProgressRepositoryImpl(
            dispatcherProvider,
            dagger.Lazy { mockCoursesRepository },
            dagger.Lazy { activitiesRepo },
            courseProgressDao,
            courseStepDao,
            examDao,
            submissionDao,
            answerDao,
            questionDao
        )

        coEvery { activitiesRepo.hasUserCompletedSync("user1") } returns true

        val result = localRepository.hasUserCompletedSync("user1")
        advanceUntilIdle()

        assertEquals(true, result)

        coEvery { activitiesRepo.hasUserCompletedSync("user1") } returns false

        val result2 = localRepository.hasUserCompletedSync("user1")
        advanceUntilIdle()

        assertEquals(false, result2)
    }

    @Test
    fun testSaveCourseProgress() = testScope.runTest {
        coEvery { courseProgressDao.findByCourseUserAndStep("course1", "user1", 1) } returns null

        repository.saveCourseProgress("user1", "planet1", "parent1", "course1", 1, true)
        advanceUntilIdle()

        coVerify {
            courseProgressDao.upsert(match { progress ->
                progress.courseId == "course1" &&
                    progress.userId == "user1" &&
                    progress.stepNum == 1 &&
                    progress.passed &&
                    progress.createdOn == "planet1" &&
                    progress.parentCode == "parent1"
            })
        }
    }

    @Test
    fun testInsertCourseProgressFromSync() = testScope.runTest {
        val doc1 = JsonObject().apply {
            addProperty("_id", "doc1")
            addProperty("courseId", "course1")
            addProperty("userId", "user1")
            addProperty("stepNum", 1)
            addProperty("passed", true)
        }
        coEvery { courseProgressDao.getByIds(listOf("doc1")) } returns emptyList()
        coEvery { courseProgressDao.getByCourseUsersAndSteps(listOf("course1"), listOf("user1"), listOf(1)) } returns emptyList()

        repository.insertCourseProgressFromSync(listOf(doc1))

        coVerify {
            courseProgressDao.upsertAll(match { progress ->
                progress.size == 1 &&
                    progress.first().id == "doc1" &&
                    progress.first()._id == "doc1" &&
                    progress.first().courseId == "course1" &&
                    progress.first().userId == "user1" &&
                    progress.first().stepNum == 1 &&
                    progress.first().passed
            })
        }
    }

    @Test
    fun testInsertCourseProgressFromSync_dedup() = testScope.runTest {
        val doc1 = JsonObject().apply {
            addProperty("_id", "doc1")
            addProperty("courseId", "course1")
            addProperty("userId", "user1")
            addProperty("stepNum", 1)
            addProperty("passed", false)
        }
        val existingProgress = CourseProgress().apply {
            id = "local1"
            _id = null
            passed = true
            courseId = "course1"
            userId = "user1"
            stepNum = 1
        }
        coEvery { courseProgressDao.getByIds(listOf("doc1")) } returns emptyList()
        coEvery { courseProgressDao.getByCourseUsersAndSteps(listOf("course1"), listOf("user1"), listOf(1)) } returns listOf(existingProgress)

        repository.insertCourseProgressFromSync(listOf(doc1))

        coVerify {
            courseProgressDao.upsertAll(match { progress ->
                progress.size == 1 &&
                    progress.first().id == "doc1" &&
                    progress.first()._id == "doc1" &&
                    progress.first().passed
            })
        }
    }

    @Test
    fun testGetCompletedCourses_nullSteps() = testScope.runTest {
        val myCourses = listOf(
            MyCourse().apply {
                courseId = "course1"
                courseTitle = "Course 1"
                courseSteps = null
            }
        )

        val progresses = listOf(
            CourseProgress().apply { courseId = "course1"; stepNum = 1; passed = true }
        )

        coEvery { mockCoursesRepository.getMyCourses("user1") } returns myCourses
        coEvery { courseProgressDao.getByUser("user1") } returns progresses

        val result = repository.getCompletedCourses("user1")
        advanceUntilIdle()

        assertEquals(0, result.size)
    }

    @Test
    fun testFindProgressForCourse() {
        val jsonArray = JsonArray()
        val course1 = JsonObject().apply {
            addProperty("courseId", "course1")
            add("progress", JsonObject().apply { addProperty("max", 10) })
        }
        val course2 = JsonObject().apply {
            addProperty("courseId", "course2")
            add("progress", JsonObject().apply { addProperty("max", 20) })
        }
        jsonArray.add(course1)
        jsonArray.add(course2)

        val result1 = repository.findProgressForCourse(jsonArray, "course1")
        assertEquals(10, result1?.get("max")?.asInt)

        val result2 = repository.findProgressForCourse(jsonArray, "course3")
        assertNull(result2)
    }

    @Test
    fun testFindProgressForCourse_emptyArray() {
        val jsonArray = JsonArray()
        val result = repository.findProgressForCourse(jsonArray, "course1")
        assertNull(result)
    }

    @Test
    fun testFindProgressForCourse_missingProgress() {
        val jsonArray = JsonArray()
        val course1 = JsonObject().apply {
            addProperty("courseId", "course1")
        }
        jsonArray.add(course1)

        val result = repository.findProgressForCourse(jsonArray, "course1")
        assertNull(result)
    }


    @Test
    fun testFetchCourseData_SubmissionsGrouping() = testScope.runTest {
        val myCourses = listOf(
            MyCourse().apply {
                courseId = "course1"
                courseTitle = "Course 1"
            },
            MyCourse().apply {
                courseId = "course10"
                courseTitle = "Course 10"
            }
        )

        val exams = listOf(
            StepExam().apply { id = "exam1"; courseId = "course1" },
            StepExam().apply { id = "exam10"; courseId = "course10" }
        )

        val questions = listOf(
            ExamQuestion().apply { id = "q1"; examId = "exam1" },
            ExamQuestion().apply { id = "q10"; examId = "exam10" }
        )

        val submissions = listOf(
            // Normal parent (course1) -> sub1 (5 mistakes)
            Submission().apply {
                id = "sub1"
                userId = "user1"
                parentId = "exam1@course1"
                type = "exam"
            },
            // Missing parent -> sub3
            Submission().apply {
                id = "sub3"
                userId = "user1"
                parentId = null
                type = "exam"
            },
            // Substring collision (course10, which contains "course1") -> sub4 (20 mistakes)
            Submission().apply {
                id = "sub4"
                userId = "user1"
                parentId = "exam10@course10"
                type = "exam"
            }
        )

        val answers = listOf(
            Answer().apply { id = "a1"; submissionId = "sub1"; questionId = "q1"; mistakes = 5 },
            Answer().apply { id = "a4"; submissionId = "sub4"; questionId = "q10"; mistakes = 20 }
        )

        coEvery { mockCoursesRepository.getMyCourses(any()) } returns myCourses
        coEvery { courseProgressDao.getByUserAndCourseIds(any(), any()) } returns emptyList()
        coEvery { courseStepDao.getByCourseIds(any()) } returns emptyList()
        coEvery { examDao.getByCourseIds(listOf("course1", "course10")) } returns exams
        coEvery { submissionDao.getExamSubmissionsByUser("user1") } returns submissions
        coEvery { answerDao.getBySubmissionIds(any()) } returns answers
        coEvery { questionDao.getByIds(any()) } returns questions

        val data = repository.fetchCourseData("user1")
        advanceUntilIdle()

        assertEquals(2, data.size())

        // Under correct grouping, course1 gets sub1 (5) = 5 mistakes.
        val obj1 = data[0].asJsonObject
        assertEquals("course1", obj1.get("courseId").asString)
        assertEquals(5, obj1.get("mistakes")?.asInt ?: 0)

        // Under correct grouping, course10 gets sub4 = 20 mistakes.
        // If substring collision fails, course10 might lose sub4 -> 0 mistakes.
        val obj10 = data[1].asJsonObject
        assertEquals("course10", obj10.get("courseId").asString)
        assertEquals(20, obj10.get("mistakes")?.asInt ?: 0)
    }

    @Test
    fun testFetchCourseData_EmptyCourses() = testScope.runTest {
        coEvery { mockCoursesRepository.getMyCourses("user1") } returns emptyList()

        val data = repository.fetchCourseData("user1")
        advanceUntilIdle()

        assertEquals(0, data.size())
    }

    @Test
    fun testGetCurrentProgress_OutOfBoundsStepNum() = testScope.runTest {
        val steps = listOf(
            CourseStep().apply { id = "step1" },
            CourseStep().apply { id = "step2" }
        )

        // Step numbers are 1-indexed for calculateCurrentProgress,
        // 0 and 3 are out of bounds for size 2
        val progresses = listOf(
            CourseProgress().apply { stepNum = 0 },
            CourseProgress().apply { stepNum = 3 }
        )

        coEvery { courseProgressDao.getByUserAndCourse("user1", "course1") } returns progresses

        val progress = repository.getCurrentProgress(steps, "user1", "course1")
        advanceUntilIdle()

        // Progress should be 0 because valid steps (1 and 2) are not completed
        assertEquals(0, progress)
    }

    @Test
    fun testGetCompletedCourses_InvalidIdOrTitle() = testScope.runTest {
        val myCourses = listOf(
            MyCourse().apply {
                courseId = "" // Invalid ID
                courseTitle = "Course 1"
                courseSteps = mutableListOf(CourseStep().apply { courseId = "" })
            },
            MyCourse().apply {
                courseId = "course2"
                courseTitle = "" // Invalid title
                courseSteps = mutableListOf(CourseStep().apply { courseId = "course2" })
            },
            MyCourse().apply {
                courseId = "course3"
                courseTitle = "Course 3"
                courseSteps = mutableListOf(CourseStep().apply { courseId = "course3" })
            }
        )

        val progresses = listOf(
            CourseProgress().apply { courseId = ""; stepNum = 1; passed = true },
            CourseProgress().apply { courseId = "course2"; stepNum = 1; passed = true },
            CourseProgress().apply { courseId = "course3"; stepNum = 1; passed = true }
        )

        coEvery { mockCoursesRepository.getMyCourses("user1") } returns myCourses
        coEvery { courseProgressDao.getByUser("user1") } returns progresses

        val result = repository.getCompletedCourses("user1")
        advanceUntilIdle()

        assertEquals(1, result.size)
        assertEquals("course3", result[0].courseId)
        assertEquals("Course 3", result[0].courseTitle)
    }

    @Test
    fun testSaveCourseProgress_UpdatesExistingRecord() = testScope.runTest {
        val existingProgress = CourseProgress().apply {
            id = "existingId"
            courseId = "course1"
            userId = "user1"
            stepNum = 1
            passed = true
        }

        coEvery { courseProgressDao.findByCourseUserAndStep("course1", "user1", 1) } returns existingProgress

        // Save progress with passed = null, existing passed should remain true
        repository.saveCourseProgress("user1", "planet1", "parent1", "course1", 1, null)
        advanceUntilIdle()

        coVerify {
            courseProgressDao.upsert(match { progress ->
                progress.id == "existingId" &&
                    progress.courseId == "course1" &&
                    progress.userId == "user1" &&
                    progress.stepNum == 1 &&
                    progress.passed == true && // passed should remain true
                    progress.createdOn == "planet1" &&
                    progress.parentCode == "parent1"
            })
        }
    }

    @Test
    fun testInsertCourseProgressFromSync_EmptyDocs() = testScope.runTest {
        repository.insertCourseProgressFromSync(emptyList())
        advanceUntilIdle()

        // DAO methods should not be called with an empty list
        coVerify(exactly = 0) { courseProgressDao.upsertAll(any()) }
    }
}
