package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmExamQuestion
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
            { mockk(relaxed = true) }
        ), recordPrivateCalls = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun fetchCourseData_executes_successfully() = runTest(testDispatcher) {
        val result = repository.fetchCourseData("user123")
        assertEquals(JsonArray(), result)
    }

    @Test
    fun testGetCurrentProgress_EmptyProgress() = testScope.runTest {
        val steps = listOf(
            RealmCourseStep().apply { id = "step1" },
            RealmCourseStep().apply { id = "step2" }
        )

        coEvery {
            repository invoke "queryList" withArguments listOf(RealmCourseProgress::class.java, any<Function1<RealmQuery<RealmCourseProgress>, Unit>>())
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
            repository invoke "queryList" withArguments listOf(RealmCourseProgress::class.java, any<Function1<RealmQuery<RealmCourseProgress>, Unit>>())
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
            repository invoke "queryList" withArguments listOf(RealmCourseProgress::class.java, any<Function1<RealmQuery<RealmCourseProgress>, Unit>>())
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
            repository invoke "queryList" withArguments listOf(RealmCourseProgress::class.java, any<Function1<RealmQuery<RealmCourseProgress>, Unit>>())
        } returns progresses

        val progress = repository.getCurrentProgress(steps, "user1", "course1")
        advanceUntilIdle()
        assertEquals(2, progress)
    }

    @Test
    fun testFetchCourseData_PopulatesFieldsCorrectly() = testScope.runTest {
        val myCourses = listOf(
            org.ole.planet.myplanet.model.RealmMyCourse().apply {
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

        coEvery {
            repository invoke "queryList" withArguments listOf(RealmCourseStep::class.java, any<Function1<RealmQuery<RealmCourseStep>, Unit>>())
        } returns steps

        coEvery {
            repository invoke "queryList" withArguments listOf(RealmCourseProgress::class.java, any<Function1<RealmQuery<RealmCourseProgress>, Unit>>())
        } returns listOf(RealmCourseProgress().apply {
            stepNum = 1
            courseId = "course1"
        })

        coEvery {
            repository invoke "queryList" withArguments listOf(RealmSubmission::class.java, any<Function1<RealmQuery<RealmSubmission>, Unit>>())
        } returns submissions

        coEvery {
            repository invoke "queryList" withArguments listOf(RealmStepExam::class.java, any<Function1<RealmQuery<RealmStepExam>, Unit>>())
        } returns exams

        coEvery {
            repository invoke "queryList" withArguments listOf(RealmAnswer::class.java, any<Function1<RealmQuery<RealmAnswer>, Unit>>())
        } returns answers

        coEvery {
            repository invoke "queryList" withArguments listOf(RealmExamQuestion::class.java, any<Function1<RealmQuery<RealmExamQuestion>, Unit>>())
        } returns listOf(question)

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

        val progresses1 = listOf(RealmCourseProgress().apply { courseId = "course1"; stepNum = 1 })

        coEvery {
            repository invoke "queryList" withArguments listOf(RealmCourseStep::class.java, any<Function1<RealmQuery<RealmCourseStep>, Unit>>())
        } returns steps1 + steps2

        coEvery {
            repository invoke "queryList" withArguments listOf(RealmCourseProgress::class.java, any<Function1<RealmQuery<RealmCourseProgress>, Unit>>())
        } returns progresses1

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
            RealmCourseProgress().apply { userId = "user1"; courseId = "course1" },
            RealmCourseProgress().apply { userId = "user1"; courseId = "course2" }
        )

        coEvery {
            repository invoke "queryList" withArguments listOf(RealmCourseProgress::class.java, any<Function1<RealmQuery<RealmCourseProgress>, Unit>>())
        } returns progresses

        val result = repository.getProgressRecords("user1")
        advanceUntilIdle()

        assertEquals(2, result.size)
        assertEquals("course1", result[0].courseId)
    }

    @Test
    fun testGetCompletedCourses() = testScope.runTest {
        val myCourses = listOf(
            org.ole.planet.myplanet.model.RealmMyCourse().apply {
                courseId = "course1"
                courseTitle = "Course 1"
                courseSteps = io.realm.RealmList(RealmCourseStep().apply { courseId = "course1" })
            },
            org.ole.planet.myplanet.model.RealmMyCourse().apply {
                courseId = "course2"
                courseTitle = "Course 2"
                courseSteps = io.realm.RealmList(RealmCourseStep().apply { courseId = "course2" }, RealmCourseStep().apply { courseId = "course2" })
            }
        )

        val progresses = listOf(
            RealmCourseProgress().apply { courseId = "course1"; stepNum = 1; passed = true },
            RealmCourseProgress().apply { courseId = "course2"; stepNum = 1; passed = true }
        )

        coEvery { mockCoursesRepository.getMyCourses("user1") } returns myCourses
        coEvery {
            repository invoke "queryList" withArguments listOf(RealmCourseProgress::class.java, any<Function1<RealmQuery<RealmCourseProgress>, Unit>>())
        } returns progresses

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
            { activitiesRepo }
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
        val mockProgress = RealmCourseProgress()

        coEvery {
            repository invoke "executeTransaction" withArguments listOf(any<Function1<io.realm.Realm, Unit>>())
        } answers {
            val transaction = firstArg<Function1<io.realm.Realm, Unit>>()
            val mockRealm = mockk<io.realm.Realm>(relaxed = true)
            val mockQuery = mockk<RealmQuery<RealmCourseProgress>>(relaxed = true)
            every { mockRealm.where(RealmCourseProgress::class.java) } returns mockQuery
            every { mockQuery.equalTo(any<String>(), any<String>()) } returns mockQuery
            every { mockQuery.equalTo(any<String>(), any<Int>()) } returns mockQuery
            every { mockQuery.findFirst() } returns null

            every { mockRealm.createObject(RealmCourseProgress::class.java, any<String>()) } returns mockProgress

            transaction.invoke(mockRealm)
        }

        repository.saveCourseProgress("user1", "planet1", "parent1", "course1", 1, true)
        advanceUntilIdle()

        io.mockk.coVerify { repository invoke "executeTransaction" withArguments listOf(any<Function1<io.realm.Realm, Unit>>()) }

        assertEquals("course1", mockProgress.courseId)
        assertEquals("user1", mockProgress.userId)
        assertEquals(1, mockProgress.stepNum)
        assertEquals(true, mockProgress.passed)
        assertEquals("planet1", mockProgress.createdOn)
        assertEquals("parent1", mockProgress.parentCode)
    }

    @Test
    fun testInsertCourseProgressFromSync() = testScope.runTest {
        val mockRealm = mockk<io.realm.Realm>(relaxed = true)
        val jsonArray = JsonArray()

        val doc1 = com.google.gson.JsonObject().apply {
            addProperty("_id", "doc1")
            addProperty("courseId", "course1")
            addProperty("userId", "user1")
            addProperty("stepNum", 1)
            addProperty("passed", true)
        }

        val mockQuery = mockk<io.realm.RealmQuery<RealmCourseProgress>>(relaxed = true)
        val mockResults = mockk<io.realm.RealmResults<RealmCourseProgress>>(relaxed = true)

        every { mockRealm.where(RealmCourseProgress::class.java) } returns mockQuery
        every { mockRealm.copyFromRealm(any<io.realm.RealmResults<RealmCourseProgress>>()) } answers { firstArg<io.realm.RealmResults<RealmCourseProgress>>().toList() }
        every { mockQuery.`in`(any<String>(), any<Array<String>>()) } returns mockQuery
        every { mockQuery.`in`(any<String>(), any<Array<Int>>()) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        every { mockResults.iterator() } returns mutableListOf<RealmCourseProgress>().iterator()

        val mockProgress = RealmCourseProgress()
        every { mockRealm.createObject(RealmCourseProgress::class.java, any<String>()) } returns mockProgress

        coEvery { repository invoke "executeTransaction" withArguments listOf(any<Function1<io.realm.Realm, Unit>>()) } answers {
            val transaction = args[0] as Function1<io.realm.Realm, Unit>
            transaction.invoke(mockRealm)
        }

        repository.insertCourseProgressFromSync(listOf(doc1))

        verify { mockRealm.where(RealmCourseProgress::class.java) }
        verify { mockRealm.createObject(RealmCourseProgress::class.java, "doc1") }

        assertEquals("doc1", mockProgress._id)
        assertEquals("course1", mockProgress.courseId)
        assertEquals("user1", mockProgress.userId)
        assertEquals(1, mockProgress.stepNum)
        assertEquals(true, mockProgress.passed)
    }

    @Test
    fun testInsertCourseProgressFromSync_dedup() = testScope.runTest {
        val mockRealm = mockk<io.realm.Realm>(relaxed = true)
        val jsonArray = JsonArray()

        val doc1 = com.google.gson.JsonObject().apply {
            addProperty("_id", "doc1")
            addProperty("courseId", "course1")
            addProperty("userId", "user1")
            addProperty("stepNum", 1)
            addProperty("passed", false)
        }

        val mockQuery = mockk<io.realm.RealmQuery<RealmCourseProgress>>(relaxed = true)
        val mockResults = mockk<io.realm.RealmResults<RealmCourseProgress>>(relaxed = true)

        every { mockRealm.where(RealmCourseProgress::class.java) } returns mockQuery
        every { mockQuery.`in`(any<String>(), any<Array<String>>()) } returns mockQuery
        every { mockQuery.`in`(any<String>(), any<Array<Int>>()) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults

        val existingProgress = mockk<RealmCourseProgress>(relaxed = true)
        every { existingProgress._id } returns null
        every { existingProgress.passed } returns true
        every { existingProgress.courseId } returns "course1"
        every { existingProgress.userId } returns "user1"
        every { existingProgress.stepNum } returns 1
        every { existingProgress.isValid } returns true

        // For the first query (ID lookup), return empty list. For the second (local records lookup), return the existing record.
        val emptyResults = mockk<io.realm.RealmResults<RealmCourseProgress>>(relaxed = true)
        every { emptyResults.iterator() } returns mutableListOf<RealmCourseProgress>().iterator()

        val localResults = mockk<io.realm.RealmResults<RealmCourseProgress>>(relaxed = true)
        every { localResults.iterator() } returns mutableListOf(existingProgress).iterator()

        every { mockQuery.findAll() } returnsMany listOf(emptyResults, localResults)
        every { mockRealm.copyFromRealm(emptyResults) } returns emptyList()
        every { mockRealm.copyFromRealm(localResults) } returns listOf(existingProgress)

        val mockProgress = RealmCourseProgress()
        every { mockRealm.createObject(RealmCourseProgress::class.java, any<String>()) } returns mockProgress

        coEvery { repository invoke "executeTransaction" withArguments listOf(any<Function1<io.realm.Realm, Unit>>()) } answers {
            val transaction = args[0] as Function1<io.realm.Realm, Unit>
            transaction.invoke(mockRealm)
        }

        repository.insertCourseProgressFromSync(listOf(doc1))

        verify { existingProgress.deleteFromRealm() }
        verify { mockRealm.createObject(RealmCourseProgress::class.java, "doc1") }

        assertEquals("doc1", mockProgress._id)
        assertEquals(true, mockProgress.passed) // Should preserve local true despite remote false
    }



    @Test
    fun testGetCompletedCourses_nullSteps() = testScope.runTest {
        val myCourses = listOf(
            org.ole.planet.myplanet.model.RealmMyCourse().apply {
                courseId = "course1"
                courseTitle = "Course 1"
                courseSteps = null
            }
        )

        val progresses = listOf(
            RealmCourseProgress().apply { courseId = "course1"; stepNum = 1; passed = true }
        )

        coEvery { mockCoursesRepository.getMyCourses("user1") } returns myCourses
        coEvery {
            repository invoke "queryList" withArguments listOf(RealmCourseProgress::class.java, any<Function1<RealmQuery<RealmCourseProgress>, Unit>>())
        } returns progresses

        val result = repository.getCompletedCourses("user1")
        advanceUntilIdle()

        assertEquals(0, result.size)
    }

    @Test
    fun testFindProgressForCourse() {
        val jsonArray = com.google.gson.JsonArray()
        val course1 = com.google.gson.JsonObject().apply {
            addProperty("courseId", "course1")
            add("progress", com.google.gson.JsonObject().apply { addProperty("max", 10) })
        }
        val course2 = com.google.gson.JsonObject().apply {
            addProperty("courseId", "course2")
            add("progress", com.google.gson.JsonObject().apply { addProperty("max", 20) })
        }
        jsonArray.add(course1)
        jsonArray.add(course2)

        val result1 = repository.findProgressForCourse(jsonArray, "course1")
        assertEquals(10, result1?.get("max")?.asInt)

        val result2 = repository.findProgressForCourse(jsonArray, "course3")
        assertEquals(null, result2)
    }
}
