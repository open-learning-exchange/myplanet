package org.ole.planet.myplanet.ui.courses

import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.services.sync.SyncManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.MainDispatcherRule

class CoursesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val coursesRepository = mockk<CoursesRepository>(relaxed = true)
    private val dispatcherProvider = mockk<DispatcherProvider>()
    private val syncManager = mockk<SyncManager>(relaxed = true)
    private val serverUrlMapper = mockk<ServerUrlMapper>(relaxed = true)
    private val prefManager = mockk<SharedPrefManager>(relaxed = true)

    private lateinit var viewModel: CoursesViewModel

    @Before
    fun setup() {
        io.mockk.every { dispatcherProvider.io } returns Dispatchers.Unconfined
        io.mockk.every { dispatcherProvider.main } returns Dispatchers.Unconfined
        viewModel = CoursesViewModel(
            coursesRepository,
            dispatcherProvider,
            syncManager,
            serverUrlMapper,
            prefManager
        )
    }

    @Test
    fun testRemoveCoursesWithProgress() = runTest {
        viewModel.removeCourses(listOf("c1", "c2"), "u1", true) {}
        coVerify { coursesRepository.removeCourseFromShelf("c1", "u1") }
        coVerify { coursesRepository.deleteCourseProgress("c1") }
        coVerify { coursesRepository.removeCourseFromShelf("c2", "u1") }
        coVerify { coursesRepository.deleteCourseProgress("c2") }
    }

    @Test
    fun testRemoveCoursesWithoutProgress() = runTest {
        viewModel.removeCourses(listOf("c1", "c2"), "u1", false) {}
        coVerify { coursesRepository.removeCourseFromShelf("c1", "u1") }
        coVerify(exactly = 0) { coursesRepository.deleteCourseProgress("c1") }
        coVerify { coursesRepository.removeCourseFromShelf("c2", "u1") }
        coVerify(exactly = 0) { coursesRepository.deleteCourseProgress("c2") }
    }

    @Test
    fun testRemoveCoursesEmpty() = runTest {
        viewModel.removeCourses(emptyList(), "u1", true) {}
        coVerify(exactly = 0) { coursesRepository.removeCourseFromShelf(any(), any()) }
        coVerify(exactly = 0) { coursesRepository.deleteCourseProgress(any()) }
    }

    @Test
    fun testLoadCourses_MyCoursesLib_CallsGetCourseProgress() = runTest {
        viewModel.loadCourses(true, "u1")
        coVerify { coursesRepository.getCourseProgress("u1", any<List<String>>()) }
    }

    @Test
    fun testLoadCourses_NotMyCoursesLib_SkipsGetCourseProgress() = runTest {
        viewModel.loadCourses(false, "u1")
        coVerify(exactly = 0) { coursesRepository.getCourseProgress(any<String>(), any<List<String>>()) }
    }
}