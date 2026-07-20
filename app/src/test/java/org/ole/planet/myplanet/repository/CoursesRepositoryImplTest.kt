package org.ole.planet.myplanet.repository

import com.google.gson.JsonParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.CertificationDao
import org.ole.planet.myplanet.data.room.dao.CourseProgressDao
import org.ole.planet.myplanet.data.room.dao.MyLibraryDao
import org.ole.planet.myplanet.data.room.dao.RemovedLogDao
import org.ole.planet.myplanet.data.room.dao.SearchActivityDao
import org.ole.planet.myplanet.data.room.dao.TagDao
import org.ole.planet.myplanet.data.room.dao.legacy.AnswerDao
import org.ole.planet.myplanet.data.room.dao.legacy.CourseDao
import org.ole.planet.myplanet.data.room.dao.legacy.CourseStepDao
import org.ole.planet.myplanet.data.room.dao.legacy.ExamDao
import org.ole.planet.myplanet.data.room.dao.legacy.QuestionDao
import org.ole.planet.myplanet.data.room.dao.legacy.SubmissionDao
import org.ole.planet.myplanet.data.room.entity.legacy.RoomCourseEntity
import org.ole.planet.myplanet.data.room.entity.legacy.RoomCourseStepEntity
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

    private lateinit var repository: CoursesRepositoryImpl

    @Before
    fun setup() {
        repository = CoursesRepositoryImpl(
            progressRepository,
            activitiesRepository,
            submissionsRepository,
            tagsRepository,
            ratingsRepository,
            sharedPrefManager,
            certificationDao,
            courseDao,
            courseStepDao,
            examDao,
            questionDao,
            submissionDao,
            answerDao,
            tagDao,
            searchActivityDao,
            courseProgressDao,
            removedLogDao,
            myLibraryDao
        )
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
            RoomCourseEntity(id = "id1", courseId = "id1", courseTitle = "Math", courseTitleNormal = "math")
        )
        coEvery { courseStepDao.getByCourseIds(any()) } returns emptyList()

        val result = repository.search("")

        assertEquals(1, result.size)
        assertEquals("Math", result.first().courseTitle)
    }

    @Test
    fun `search filters query parts before fetching and sorts startsWith before contains`() = runTest {
        coEvery { courseDao.getAll() } returns listOf(
            RoomCourseEntity(id = "1", courseId = "1", courseTitle = "Basic Math", courseTitleNormal = "basic math"),
            RoomCourseEntity(id = "2", courseId = "2", courseTitle = "Science", courseTitleNormal = "science"),
            RoomCourseEntity(id = "3", courseId = "3", courseTitle = "Math 101", courseTitleNormal = "math 101")
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
            RoomCourseEntity(id = "1", courseId = "1", courseTitle = "Basic Math 101", courseTitleNormal = "basic math 101"),
            RoomCourseEntity(id = "2", courseId = "2", courseTitle = "Basic Science 101", courseTitleNormal = "basic science 101")
        )
        coEvery { courseStepDao.getByCourseIds(any()) } returns emptyList()

        val result = repository.search("Basic Math")

        assertEquals(1, result.size)
        assertEquals("Basic Math 101", result[0].courseTitle)
    }

    @Test
    fun `getCoursesByIds returns correct courses`() = runTest {
        coEvery { courseDao.getByCourseIds(listOf("id1", "id2")) } returns listOf(
            RoomCourseEntity(id = "id1", courseId = "id1", courseTitle = "Course 1"),
            RoomCourseEntity(id = "id2", courseId = "id2", courseTitle = "Course 2")
        )
        coEvery { courseStepDao.getByCourseIds(listOf("id1", "id2")) } returns listOf(
            RoomCourseStepEntity(id = "step1", courseId = "id1", stepTitle = "Step 1"),
            RoomCourseStepEntity(id = "step2", courseId = "id2", stepTitle = "Step 2")
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
}
