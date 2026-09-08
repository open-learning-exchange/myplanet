package org.ole.planet.myplanet.repository

import com.google.gson.JsonParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.AnswerDao
import org.ole.planet.myplanet.data.room.dao.CertificationDao
import org.ole.planet.myplanet.data.room.dao.CourseDao
import org.ole.planet.myplanet.data.room.dao.CourseProgressDao
import org.ole.planet.myplanet.data.room.dao.CourseStepDao
import org.ole.planet.myplanet.data.room.dao.ExamDao
import org.ole.planet.myplanet.data.room.dao.MyLibraryDao
import org.ole.planet.myplanet.data.room.dao.QuestionDao
import org.ole.planet.myplanet.data.room.dao.RemovedLogDao
import org.ole.planet.myplanet.data.room.dao.SearchActivityDao
import org.ole.planet.myplanet.data.room.dao.SubmissionDao
import org.ole.planet.myplanet.data.room.dao.TagDao
import org.ole.planet.myplanet.model.CourseStep
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.model.SearchActivity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.Utilities

@OptIn(ExperimentalCoroutinesApi::class)
class CoursesRepositoryImplTest {

    private val progressRepository: ProgressRepository = mockk(relaxed = true)
    private val activitiesRepository: ActivitiesRepository = mockk(relaxed = true)
    private val submissionsRepository: SubmissionsRepository = mockk(relaxed = true)
    private val tagsRepository: TagsRepository = mockk(relaxed = true)
    private val ratingsRepository: RatingsRepository = mockk(relaxed = true)
    private val resourcesRepository: ResourcesRepository = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)
    private val certificationDao: CertificationDao = mockk(relaxed = true)
    private val courseDao: CourseDao = mockk(relaxed = true)
    private val courseStepDao: CourseStepDao = mockk(relaxed = true)
    private val examDao: ExamDao = mockk(relaxed = true)
    private val questionDao: QuestionDao = mockk(relaxed = true)
    private val submissionDao: SubmissionDao = mockk(relaxed = true)
    private val answerDao: AnswerDao = mockk(relaxed = true)
    private val tagDao: TagDao = mockk(relaxed = true)
    private val searchActivityDao: SearchActivityDao = mockk(relaxed = true)
    private val courseProgressDao: CourseProgressDao = mockk(relaxed = true)
    private val removedLogDao: RemovedLogDao = mockk(relaxed = true)
    private val myLibraryDao: MyLibraryDao = mockk(relaxed = true)
    private val userRepository: dagger.Lazy<UserRepository> = mockk(relaxed = true)
    private val dispatcherProvider: org.ole.planet.myplanet.utils.DispatcherProvider = object : org.ole.planet.myplanet.utils.DispatcherProvider { override val main = kotlinx.coroutines.Dispatchers.Unconfined; override val io = kotlinx.coroutines.Dispatchers.Unconfined; override val default = kotlinx.coroutines.Dispatchers.Unconfined; override val unconfined = kotlinx.coroutines.Dispatchers.Unconfined }
    private val realtimeSyncManager: org.ole.planet.myplanet.services.sync.RealtimeSyncManager = mockk(relaxed = true)

    private lateinit var repository: CoursesRepositoryImpl

    @Before
    fun setup() {
        repository = CoursesRepositoryImpl(
            progressRepository,
            activitiesRepository,
            submissionsRepository,
            tagsRepository,
            ratingsRepository,
            resourcesRepository,
            sharedPrefManager,
            certificationDao,
            courseDao,
            courseStepDao,
            examDao,
            questionDao,
            submissionDao,
            answerDao,
            searchActivityDao,
            courseProgressDao,
            removedLogDao,
            myLibraryDao,
            userRepository,
            dispatcherProvider,
        realtimeSyncManager,
        mockk(relaxed = true)
        )
    }

    @Test
    fun testUpdateCourseProgressScopesUpdateToTheGivenUser() = runTest {
        coEvery { courseProgressDao.updatePassedByCourseAndStep(any(), any(), any(), any()) } returns 1

        repository.updateCourseProgress("course1", 2, true, "user-a")

        coVerify { courseProgressDao.updatePassedByCourseAndStep("course1", 2, true, "user-a") }
    }

    @Test
    fun testNormalizeText() {
        assertEquals("hello world", Utilities.normalizeText("HELLO World"))
        assertEquals("cafe", Utilities.normalizeText("Café"))
        assertEquals("nino", Utilities.normalizeText("Niño"))
        assertEquals("a e i o u", Utilities.normalizeText("á é í ó ú"))
        assertEquals("c", Utilities.normalizeText("ç"))
        assertEquals("aeiou", Utilities.normalizeText("äëïöü"))
    }

    @Test
    fun testMatchesAllParts() {
        assertTrue(repository.matchesAllParts("hello world", listOf("hello", "world")))
        assertFalse(repository.matchesAllParts("hello world", listOf("hello", "universe")))
        assertTrue(repository.matchesAllParts("the quick brown fox", listOf("quick", "fox")))
        assertTrue(repository.matchesAllParts("test", emptyList<String>()))
    }

    @Test
    fun `search empty query returns all courses`() = runTest {
        coEvery { courseDao.getAll() } returns listOf(
            MyCourse(id = "id1", courseId = "id1", courseTitle = "Math", courseTitleNormal = "math")
        )
        coEvery { courseStepDao.getByCourseIds(any()) } returns emptyList()

        val result = repository.search("")

        assertEquals(1, result.size)
        assertEquals("Math", result.first().courseTitle)
    }

    @Test
    fun `search filters query parts before fetching and sorts startsWith before contains`() = runTest {
        coEvery { courseDao.getAll() } returns listOf(
            MyCourse(id = "1", courseId = "1", courseTitle = "Basic Math", courseTitleNormal = "basic math"),
            MyCourse(id = "2", courseId = "2", courseTitle = "Science", courseTitleNormal = "science"),
            MyCourse(id = "3", courseId = "3", courseTitle = "Math 101", courseTitleNormal = "math 101")
        )
        coEvery { courseStepDao.getByCourseIds(any()) } returns emptyList()

        val result = repository.search("Math")

        assertEquals(2, result.size)
        assertEquals("Math 101", result[0].courseTitle)
        assertEquals("Basic Math", result[1].courseTitle)
    }

    @Test
    fun `search multi word matches all parts`() = runTest {
        coEvery { courseDao.getAll() } returns listOf(
            MyCourse(id = "1", courseId = "1", courseTitle = "Basic Math 101", courseTitleNormal = "basic math 101"),
            MyCourse(id = "2", courseId = "2", courseTitle = "Basic Science 101", courseTitleNormal = "basic science 101")
        )
        coEvery { courseStepDao.getByCourseIds(any()) } returns emptyList()

        val result = repository.search("Basic Math")

        assertEquals(1, result.size)
        assertEquals("Basic Math 101", result[0].courseTitle)
    }

    @Test
    fun `filterCourses sorts titles case-insensitively`() = runTest {
        coEvery { courseDao.getAll() } returns listOf(
            MyCourse(id = "1", courseId = "1", courseTitle = "banana", courseTitleNormal = "banana"),
            MyCourse(id = "2", courseId = "2", courseTitle = "Apple", courseTitleNormal = "apple"),
            MyCourse(id = "3", courseId = "3", courseTitle = "cherry", courseTitleNormal = "cherry")
        )
        coEvery { courseStepDao.getByCourseIds(any()) } returns emptyList()
        coEvery { tagsRepository.getLinkIdsForTagNames(any(), any()) } returns emptyList()

        val result = repository.filterCourses("", "", "", emptyList())

        assertEquals(3, result.size)
        assertEquals("Apple", result[0].courseTitle)
        assertEquals("banana", result[1].courseTitle)
        assertEquals("cherry", result[2].courseTitle)
    }

    @Test
    fun `getCoursesByIds returns correct courses`() = runTest {
        coEvery { courseDao.getByCourseIds(listOf("id1", "id2")) } returns listOf(
            MyCourse(id = "id1", courseId = "id1", courseTitle = "Course 1"),
            MyCourse(id = "id2", courseId = "id2", courseTitle = "Course 2")
        )
        coEvery { courseStepDao.getByCourseIds(listOf("id1", "id2")) } returns listOf(
            CourseStep(id = "step1", courseId = "id1", stepTitle = "Step 1"),
            CourseStep(id = "step2", courseId = "id2", stepTitle = "Step 2")
        )

        val result = repository.getCoursesByIds(listOf("id1", "id2"))

        assertEquals(2, result.size)
        assertEquals("Course 1", result[0].courseTitle)
        assertEquals("Step 1", result[0].courseSteps?.first()?.stepTitle)
        assertEquals("Course 2", result[1].courseTitle)
    }

    @Test
    fun `saveSearchActivity writes course search activity to Room`() = runTest {
        val savedActivity = slot<SearchActivity>()

        repository.saveSearchActivity(
            searchText = "algebra",
            userName = "learner",
            planetCode = "planet",
            parentCode = "parent",
            tags = emptyList(),
            grade = "6",
            subject = "math"
        )

        coVerify(exactly = 1) { searchActivityDao.insert(capture(savedActivity)) }
        assertTrue(savedActivity.captured.id.isNotBlank())
        assertEquals("learner", savedActivity.captured.user)
        assertEquals("planet", savedActivity.captured.createdOn)
        assertEquals("parent", savedActivity.captured.parentCode)
        assertEquals("algebra", savedActivity.captured.text)
        assertEquals("courses", savedActivity.captured.type)

        val filter = JsonParser.parseString(savedActivity.captured.filter).asJsonObject
        assertEquals("6", filter["doc.gradeLevel"].asString)
        assertEquals("math", filter["doc.subjectLevel"].asString)
        assertTrue(filter.getAsJsonArray("tags").isEmpty)
    }

    @Test
    fun `getMyCourses with list filters correctly by userId`() {
        val course1 = MyCourse(id = "1", userId = listOf("user1", "user2"))
        val course2 = MyCourse(id = "2", userId = listOf("user2"))
        val course3 = MyCourse(id = "3", userId = null)
        val courses = listOf(course1, course2, course3)

        val resultNull = repository.getMyCourses(null, courses)
        assertTrue(resultNull.isEmpty())

        val resultUser1 = repository.getMyCourses("user1", courses)
        assertEquals(1, resultUser1.size)
        assertEquals("1", resultUser1[0].id)

        val resultUser2 = repository.getMyCourses("user2", courses)
        assertEquals(2, resultUser2.size)

        val resultUser3 = repository.getMyCourses("user3", courses)
        assertTrue(resultUser3.isEmpty())
    }

    @Test
    fun `getMyCourses by userId fetches all and filters`() = runTest {
        coEvery { courseDao.getForUserPattern(any()) } answers {
            val userPattern = arg<String>(0)
            val userId = userPattern.removeSurrounding("%\"", "\"%")
            listOf(
                MyCourse(id = "1", userId = listOf("user1", "user2")),
                MyCourse(id = "2", userId = listOf("user2")),
                MyCourse(id = "3", userId = null)
            ).filter { it.userId?.contains(userId) == true }
        }
        coEvery { courseStepDao.getByCourseIds(any()) } returns emptyList()

        val resultUser1 = repository.getMyCourses("user1")
        assertEquals(1, resultUser1.size)
        assertEquals("1", resultUser1[0].id)

        val resultUser2 = repository.getMyCourses("user2")
        assertEquals(2, resultUser2.size)

        val resultUser3 = repository.getMyCourses("user3")
        assertTrue(resultUser3.isEmpty())
    }

    @Test
    fun `getMyCoursesFlow suppresses redundant emissions`() = runTest {
        val course = MyCourse(id = "1", userId = listOf("user1"))
        // Create an identical copy simulating Room's recreation on query
        val identicalCourse = MyCourse(id = "1", userId = listOf("user1"))

        coEvery { courseDao.observeForUserPattern(any()) } returns flowOf(listOf(course), listOf(identicalCourse))
        coEvery { courseStepDao.getByCourseIds(any()) } returns emptyList()

        val emissions = repository.getMyCoursesFlow("user1").toList()

        // DAO emitted twice, but lists are logically identical, so downstream should receive only 1 emission
        assertEquals(1, emissions.size)
        assertEquals(1, emissions[0].size)
        assertEquals("1", emissions[0][0].id)
    }

    @Test
    fun `getCourseByCourseIdFlow returns mapped course with steps`() = runTest {
        val courseId = "course-123"
        val myCourse = MyCourse(id = courseId, courseId = courseId, courseTitle = "Test Course")
        val steps = listOf(
            CourseStep(id = "step-1", courseId = courseId, stepTitle = "Step 1"),
            CourseStep(id = "step-2", courseId = courseId, stepTitle = "Step 2")
        )

        every { courseDao.observeByCourseId(courseId) } returns flowOf(myCourse)
        coEvery { courseStepDao.getByCourseId(courseId) } returns steps

        val resultFlow = repository.getCourseByCourseIdFlow(courseId)
        val mappedCourse = resultFlow.first()

        assertNotNull(mappedCourse)
        assertEquals(courseId, mappedCourse?.courseId)
        assertEquals("Test Course", mappedCourse?.courseTitle)
        assertEquals(2, mappedCourse?.courseSteps?.size)
        assertEquals(2, mappedCourse?.getNumberOfSteps())
        assertEquals("Step 1", mappedCourse?.courseSteps?.get(0)?.stepTitle)
    }

    @Test
    fun `getCourseByCourseIdFlow returns null when course not found`() = runTest {
        val courseId = "non-existent-course"

        every { courseDao.observeByCourseId(courseId) } returns flowOf(null)

        val resultFlow = repository.getCourseByCourseIdFlow(courseId)
        val mappedCourse = resultFlow.first()

        assertNull(mappedCourse)
    }

    @Test
    fun getCourseDetailModel_whenCourseNull_returnsNull() = runTest {
        coEvery { courseDao.observeByCourseId(any()) } returns kotlinx.coroutines.flow.flowOf(null)

        val result = repository.getCourseDetailModel("course_id").firstOrNull()
        assertNull(result)
    }

    @Test
    fun `flushPendingCourseResources batches existing DAO queries`() = runTest {
        val jsonArray = com.google.gson.JsonArray().apply {
            add(com.google.gson.JsonObject().apply { addProperty("_id", "resource1") })
            add(com.google.gson.JsonObject().apply { addProperty("_id", "resource2") })
        }

        // Use reflection to enqueue items into the private pendingCourseResources list
        // This isolates the test without expanding the public API of the repository.
        val queueMethod = CoursesRepositoryImpl::class.java.getDeclaredMethod(
            "queueCourseResources",
            String::class.java,
            String::class.java,
            com.google.gson.JsonArray::class.java
        )
        queueMethod.isAccessible = true
        queueMethod.invoke(repository, "courseId", "stepId", jsonArray)

        val existingResource = org.ole.planet.myplanet.model.MyLibrary().apply { id = "resource1" }
        coEvery { myLibraryDao.getByIds(listOf("resource1", "resource2")) } returns listOf(existingResource)
        coEvery { myLibraryDao.upsertAll(any()) } returns Unit

        repository.flushPendingCourseResources()

        coVerify(exactly = 1) { myLibraryDao.getByIds(listOf("resource1", "resource2")) }
        coVerify(exactly = 1) { myLibraryDao.upsertAll(any()) }
    }

    @Test
    fun getCourseDetailModel_whenCourseExists_returnsAggregatedData() = runTest {
        val course = MyCourse().apply { courseId = "course_id" }
        val step = org.ole.planet.myplanet.model.CourseStep().apply { id = "step_1"; stepTitle = "Title" }
        val user = org.ole.planet.myplanet.model.UserEntity().apply { id = "user_1" }

        coEvery { courseDao.observeByCourseId("course_id") } returns kotlinx.coroutines.flow.flowOf(course)
        coEvery { userRepository.get().getUserModel() } returns user
        coEvery { examDao.countByCourseIdAndType("course_id", "courses") } returns 5
        coEvery { myLibraryDao.getCourseResources("course_id", false) } returns emptyList()
        coEvery { myLibraryDao.getCourseResources("course_id", true) } returns emptyList()
        coEvery { courseStepDao.getByCourseId("course_id") } returns listOf(org.ole.planet.myplanet.model.CourseStep().apply { id = "step_1"; stepTitle = "Title" })
        coEvery { submissionsRepository.getExamQuestionCount("step_1") } returns 3

        coEvery { ratingsRepository.getRatingSummary("course", "course_id", "user_1") } returns RatingSummary(
            existingRating = null,
            averageRating = 4.0f,
            totalRatings = 2,
            userRating = 5
        )

        val result = repository.getCourseDetailModel("course_id").firstOrNull()

        assertNotNull(result)
        assertEquals(course, result?.course)
        assertEquals(user, result?.user)
        assertEquals(5, result?.examCount)
        assertEquals(1, result?.steps?.size)
        assertEquals("step_1", result?.steps?.first()?.id)
        assertEquals(3, result?.steps?.first()?.questionCount)
        assertEquals(4.0f, result?.ratingSummary?.averageRating)
    }

    @Test
    fun `joinCourse merges user ids deduplicating pre-existing entries and preserving order`() = runTest {
        val existingCourse = MyCourse(
            id = "course-123",
            courseId = "course-123",
            userId = listOf("user1", "", "user2", "user1")
        )
        coEvery { courseDao.getByCourseId("course-123") } returns existingCourse
        val capturedCourse = slot<MyCourse>()
        coEvery { courseDao.upsert(capture(capturedCourse)) } returns Unit

        val result = repository.joinCourse("course-123", "user3")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { courseDao.upsert(any()) }
        assertEquals(listOf("user1", "user2", "user3"), capturedCourse.captured.userId)
    }

    @Test
    fun `markCoursesAdded merges user ids deduplicating pre-existing entries and preserving order`() = runTest {
        val existingCourse = MyCourse(
            id = "course-1",
            courseId = "course-1",
            userId = listOf("userA", "userB", "userA")
        )
        coEvery { courseDao.getByCourseIds(listOf("course-1")) } returns listOf(existingCourse)
        val capturedCourses = slot<List<MyCourse>>()
        coEvery { courseDao.upsertAll(capture(capturedCourses)) } returns Unit

        val result = repository.markCoursesAdded(listOf("course-1"), "userC")

        assertTrue(result.getOrDefault(false))
        coVerify(exactly = 1) { courseDao.upsertAll(any()) }
        assertEquals(listOf("userA", "userB", "userC"), capturedCourses.captured.first().userId)
    }
}
