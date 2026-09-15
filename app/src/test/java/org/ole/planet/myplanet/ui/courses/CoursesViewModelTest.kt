package org.ole.planet.myplanet.ui.courses

import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.Course
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class CoursesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val coursesRepository = mockk<CoursesRepository>(relaxed = true)
    private val progressRepository = mockk<ProgressRepository>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    private lateinit var viewModel: CoursesViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CoursesViewModel(
            coursesRepository,
            progressRepository,
            dispatcherProvider
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testRemoveCoursesWithProgress() = runTest {
        viewModel.removeCourses(listOf("c1", "c2"), "u1", true) {}
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { coursesRepository.removeCoursesFromShelf(listOf("c1", "c2"), "u1") }
        coVerify { coursesRepository.deleteCoursesProgress(listOf("c1", "c2")) }
    }

    @Test
    fun testRemoveCoursesWithoutProgress() = runTest {
        viewModel.removeCourses(listOf("c1", "c2"), "u1", false) {}
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { coursesRepository.removeCoursesFromShelf(listOf("c1", "c2"), "u1") }
        coVerify(exactly = 0) { coursesRepository.deleteCoursesProgress(any()) }
    }

    @Test
    fun testRemoveCoursesEmpty() = runTest {
        viewModel.removeCourses(emptyList(), "u1", true) {}
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 0) { coursesRepository.removeCoursesFromShelf(any(), any()) }
        coVerify(exactly = 0) { coursesRepository.deleteCoursesProgress(any()) }
    }

    @Test
    fun testLoadCourses_MyCoursesLib_CallsGetCourseProgress() = runTest {
        viewModel.loadCourses(true, "u1")
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { progressRepository.getCourseProgress(any<List<String>>(), "u1") }
    }

    @Test
    fun testLoadCourses_NotMyCoursesLib_StillCallsGetCourseProgress() = runTest {
        viewModel.loadCourses(false, "u1")
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { progressRepository.getCourseProgress(any<List<String>>(), "u1") }
    }

    @Test
    fun testFilterCourses_ProgressFilter_CallsRepositoryFilter() = runTest {
        viewModel.filterCourses(false, "u1", "", "", "", emptyList(), "In Progress")
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { coursesRepository.filterCourses("", "", "", emptyList()) }
        coVerify { coursesRepository.getMyCourses("u1", any()) }
    }

    @Test
    fun testToggleSort_correctlySortsCourses() = runTest {
        val course1 = Course(
            courseId = "1",
            courseTitle = "Zebra",
            description = "desc",
            gradeLevel = "grade",
            subjectLevel = "subject",
            createdDate = 1000L
        )
        val course2 = Course(
            courseId = "2",
            courseTitle = "Apple",
            description = "desc",
            gradeLevel = "grade",
            subjectLevel = "subject",
            createdDate = 2000L
        )
        val course3 = Course(
            courseId = "3",
            courseTitle = "Banana",
            description = "desc",
            gradeLevel = "grade",
            subjectLevel = "subject",
            createdDate = 1500L
        )

        val myCourse1 = MyCourse().apply {
            courseId = "1"
            courseTitle = "Zebra"
            createdDate = 1000L
        }
        val myCourse2 = MyCourse().apply {
            courseId = "2"
            courseTitle = "Apple"
            createdDate = 2000L
        }
        val myCourse3 = MyCourse().apply {
            courseId = "3"
            courseTitle = "Banana"
            createdDate = 1500L
        }

        io.mockk.coEvery { coursesRepository.getAllCourses() } returns listOf(myCourse1, myCourse2, myCourse3)
        io.mockk.coEvery { coursesRepository.getMyCourses(any(), any()) } returns emptyList()

        viewModel.loadCourses(false, "u1")
        testDispatcher.scheduler.advanceUntilIdle()

        // Default sort might vary based on how it's initialized, so let's check toggleTitleSort (descending -> ascending)
        viewModel.toggleTitleSort() // Ascending
        testDispatcher.scheduler.advanceUntilIdle()

        var courses = viewModel.coursesState.value.courses
        assertEquals("Apple", courses[0].courseTitle)
        assertEquals("Banana", courses[1].courseTitle)
        assertEquals("Zebra", courses[2].courseTitle)

        viewModel.toggleTitleSort() // Descending
        testDispatcher.scheduler.advanceUntilIdle()

        courses = viewModel.coursesState.value.courses
        assertEquals("Zebra", courses[0].courseTitle)
        assertEquals("Banana", courses[1].courseTitle)
        assertEquals("Apple", courses[2].courseTitle)

        viewModel.toggleDateSort() // Ascending (default was true, first toggle is false -> Descending)
        testDispatcher.scheduler.advanceUntilIdle()

        courses = viewModel.coursesState.value.courses
        assertEquals("Apple", courses[0].courseTitle) // 2000L
        assertEquals("Banana", courses[1].courseTitle) // 1500L
        assertEquals("Zebra", courses[2].courseTitle) // 1000L

        viewModel.toggleDateSort() // Ascending
        testDispatcher.scheduler.advanceUntilIdle()

        courses = viewModel.coursesState.value.courses
        assertEquals("Zebra", courses[0].courseTitle) // 1000L
        assertEquals("Banana", courses[1].courseTitle) // 1500L
        assertEquals("Apple", courses[2].courseTitle) // 2000L
    }

    @Test
    fun testFilterCourses_updatesCurrentFilterState() = runTest {
        val tags = listOf("Science", "Math")
        viewModel.filterCourses(false, "u1", "query", "Grade 1", "Math", tags, "In Progress")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.currentFilterState
        assertEquals("query", state.searchText)
        assertEquals("Grade 1", state.grade)
        assertEquals("Math", state.subject)
        assertEquals(tags, state.tagNames)
        assertEquals("In Progress", state.progressFilter)
    }

    @Test
    fun testLoadCourses_withActiveCurrentFilterState_usesFilterCoursesInternal() = runTest {
        viewModel.filterCourses(false, "u1", "algebra", "", "", listOf("Science"), "")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.loadCourses(false, "u1")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(atLeast = 2) { coursesRepository.filterCourses("algebra", "", "", listOf("Science")) }
    }

    @Test
    fun testFilterCourses_tagPreservation() = runTest {
        val tagNames = listOf("Biology", "Chemistry")
        viewModel.filterCourses(true, "u1", "", "", "", tagNames, "")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.currentFilterState
        assertEquals(tagNames, state.tagNames)
        coVerify { coursesRepository.filterCourses("", "", "", tagNames) }
    }

    @Test
    fun testTitleSort_mixedCase_caseInsensitive() = runTest {
        val c1 = MyCourse().apply { courseId = "1"; courseTitle = "apple" }
        val c2 = MyCourse().apply { courseId = "2"; courseTitle = "Banana" }
        val c3 = MyCourse().apply { courseId = "3"; courseTitle = "cherry" }
        val c4 = MyCourse().apply { courseId = "4"; courseTitle = "Date" }

        io.mockk.coEvery { coursesRepository.getAllCourses() } returns listOf(c1, c2, c3, c4)
        io.mockk.coEvery { coursesRepository.getMyCourses(any(), any()) } returns emptyList()

        viewModel.loadCourses(false, "u1")
        testDispatcher.scheduler.advanceUntilIdle()

        // Toggle title sort -> Ascending
        viewModel.toggleTitleSort()
        testDispatcher.scheduler.advanceUntilIdle()

        var titles = viewModel.coursesState.value.courses.map { it.courseTitle }
        assertEquals(listOf("apple", "Banana", "cherry", "Date"), titles)

        // Toggle title sort -> Descending
        viewModel.toggleTitleSort()
        testDispatcher.scheduler.advanceUntilIdle()

        titles = viewModel.coursesState.value.courses.map { it.courseTitle }
        assertEquals(listOf("Date", "cherry", "Banana", "apple"), titles)
    }

    @Test
    fun testTitleSort_caseDifferenceOnly_preservesRelativeOrderInBothDirections() = runTest {
        val c1 = MyCourse().apply { courseId = "1"; courseTitle = "Alpha" }
        val c2 = MyCourse().apply { courseId = "2"; courseTitle = "alpha" }

        io.mockk.coEvery { coursesRepository.getAllCourses() } returns listOf(c1, c2)
        io.mockk.coEvery { coursesRepository.getMyCourses(any(), any()) } returns emptyList()

        viewModel.loadCourses(false, "u1")
        testDispatcher.scheduler.advanceUntilIdle()

        // Toggle title sort -> Ascending
        viewModel.toggleTitleSort()
        testDispatcher.scheduler.advanceUntilIdle()

        var ids = viewModel.coursesState.value.courses.map { it.courseId }
        assertEquals(listOf("1", "2"), ids)

        // Toggle title sort -> Descending
        viewModel.toggleTitleSort()
        testDispatcher.scheduler.advanceUntilIdle()

        ids = viewModel.coursesState.value.courses.map { it.courseId }
        assertEquals(listOf("1", "2"), ids)
    }

    @Test
    fun testDateSort_unaffected() = runTest {
        val c1 = MyCourse().apply { courseId = "1"; courseTitle = "Course A"; createdDate = 2000L }
        val c2 = MyCourse().apply { courseId = "2"; courseTitle = "Course B"; createdDate = 1000L }

        io.mockk.coEvery { coursesRepository.getAllCourses() } returns listOf(c1, c2)
        io.mockk.coEvery { coursesRepository.getMyCourses(any(), any()) } returns emptyList()

        viewModel.loadCourses(false, "u1")
        testDispatcher.scheduler.advanceUntilIdle()

        // Toggle date sort -> isDateAscending starts as true, toggle makes it false (Descending)
        viewModel.toggleDateSort()
        testDispatcher.scheduler.advanceUntilIdle()

        var dates = viewModel.coursesState.value.courses.map { it.createdDate }
        assertEquals(listOf(2000L, 1000L), dates)

        // Toggle date sort -> Ascending
        viewModel.toggleDateSort()
        testDispatcher.scheduler.advanceUntilIdle()

        dates = viewModel.coursesState.value.courses.map { it.createdDate }
        assertEquals(listOf(1000L, 2000L), dates)
    }
}
