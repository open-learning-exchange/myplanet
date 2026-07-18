package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.room.dao.CourseProgressDao
import org.ole.planet.myplanet.data.room.dao.legacy.AnswerDao
import org.ole.planet.myplanet.data.room.dao.legacy.CourseStepDao
import org.ole.planet.myplanet.data.room.dao.legacy.ExamDao
import org.ole.planet.myplanet.data.room.dao.legacy.QuestionDao
import org.ole.planet.myplanet.data.room.dao.legacy.SubmissionDao
import org.ole.planet.myplanet.data.room.entity.legacy.RoomExamEntity
import org.ole.planet.myplanet.data.room.entity.legacy.RoomSubmissionEntity
import org.ole.planet.myplanet.data.room.entity.legacy.RoomCourseStepEntity
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.CourseProgress
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
    private lateinit var mockCoursesRepository: CoursesRepository
    private val courseProgressDao: CourseProgressDao = mockk(relaxed = true)
    private val courseStepDao: CourseStepDao = mockk(relaxed = true)
    private val examDao: ExamDao = mockk(relaxed = true)
    private val submissionDao: SubmissionDao = mockk(relaxed = true)
    private val answerDao: AnswerDao = mockk(relaxed = true)
    private val questionDao: QuestionDao = mockk(relaxed = true)

    @Before
    fun setUp() {
        every { dispatcherProvider.io } returns testDispatcher
        mockCoursesRepository = mockk<CoursesRepository>()
        coEvery { mockCoursesRepository.getMyCourses(any()) } returns emptyList()
        repository = spyk(ProgressRepositoryImpl(
            databaseService,
            UnconfinedTestDispatcher(),
            dispatcherProvider,
            { mockCoursesRepository },
            { mockk(relaxed = true) },
            courseProgressDao,
            courseStepDao,
            examDao,
            submissionDao,
            answerDao,
            questionDao
        ), recordPrivateCalls = true)
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
            RealmCourseStep().apply { id = "step1" },
            RealmCourseStep().apply { id = "step2" }
        )

        coEvery { courseProgressDao.getByUserAndCourse("user1", "course1") } returns emptyList()

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
            CourseProgress().apply { stepNum = 1 },
            CourseProgress().apply { stepNum = 3 }
        )

        coEvery { courseProgressDao.getByUserAndCourse("user1", "course1") } returns progresses

        var progress = repository.getCurrentProgress(steps, "user1", "course1")
        advanceUntilIdle()
        assertEquals(1, progress)

        // Now test where 1, 2, 3 are present, 4 is missing, to ensure it doesn't just always return 1
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
            RealmCourseStep().apply { id = "step1" },
            RealmCourseStep().apply { id = "step2" }
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

        coEvery { mockCoursesRepository.getMyCourses(any()) } returns myCourses

        coEvery { courseStepDao.getByCourseIds(listOf("course1")) } returns steps.mapIndexed { index, step ->
            RoomCourseStepEntity(id = step.id ?: "step$index", courseId = step.courseId)
        }

        coEvery { courseProgressDao.getByUserAndCourseIds("user1", listOf("course1")) } returns listOf(CourseProgress().apply {
            stepNum = 1
            courseId = "course1"
        })

        coEvery { submissionDao.getExamSubmissionsByUser("user1") } returns submissions.map { submission ->
            RoomSubmissionEntity(id = submission.id ?: "submission", parentId = submission.parentId, userId = submission.userId, type = submission.type)
        }

        coEvery { examDao.getByCourseIds(listOf("course1")) } returns exams.map { exam ->
            RoomExamEntity(id = exam.id ?: "exam", courseId = exam.courseId, stepId = exam.stepId, type = exam.type)
        }

        coEvery { answerDao.getBySubmissionIds(listOf("sub1")) } returns answers.map { answer ->
            org.ole.planet.myplanet.data.room.entity.legacy.RoomAnswerEntity(
                id = answer.id ?: "answer",
                questionId = answer.questionId,
                submissionId = answer.submissionId,
                mistakes = answer.mistakes,
            )
        }

        coEvery { questionDao.getByIds(listOf("q1")) } returns listOf(
            org.ole.planet.myplanet.data.room.entity.legacy.RoomQuestionEntity(id = "q1", examId = "exam1")
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
        assertEquals(2, stepMistake.get("0").asInt) // Exam 1 is at index 0
    }
    @Test
    fun testGetCourseProgress() = testScope.runTest {
        val courseIds = listOf("course1", "course2")
        val steps1 = listOf(RealmCourseStep().apply { courseId = "course1" })
        val steps2 = listOf(RealmCourseStep().apply { courseId = "course2" }, RealmCourseStep().apply { courseId = "course2" })

        val progresses1 = listOf(CourseProgress().apply { courseId = "course1"; stepNum = 1 })

        coEvery { courseStepDao.getByCourseIds(courseIds) } returns (steps1 + steps2).mapIndexed { index, step ->
            RoomCourseStepEntity(id = step.id ?: "step$index", courseId = step.courseId)
        }

        coEvery { courseProgressDao.getByUserAndCourseIds("user1", courseIds) } returns progresses1

        val result = repository.getCourseProgress(courseIds, "user1")
        advanceUntilIdle()

        assertEquals(2, result.size)
        assertEquals(1, result["course1"]?.get("max")?.asInt)
        assertEquals(1, result["course1"]?.get("current")?.asInt)

        assertEquals(2, result["course2"]?.get("max")?.asInt)
        assertEquals(0, result["course2"]?.get("current")?.asInt)
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
            RealmMyCourse().apply {
                courseId = "course1"
                courseTitle = "Course 1"
                courseSteps = io.realm.RealmList(RealmCourseStep().apply { courseId = "course1" })
            },
            RealmMyCourse().apply {
                courseId = "course2"
                courseTitle = "Course 2"
                courseSteps = io.realm.RealmList(RealmCourseStep().apply { courseId = "course2" }, RealmCourseStep().apply { courseId = "course2" })
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
            databaseService,
            UnconfinedTestDispatcher(),
            dispatcherProvider,
            { mockCoursesRepository },
            { activitiesRepo },
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
            RealmMyCourse().apply {
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
        assertEquals(null, result2)
    }
}
