package org.ole.planet.myplanet.ui.courses

import android.content.SharedPreferences
import com.google.gson.JsonObject
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import io.realm.RealmList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.repository.CourseProgressRepository
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.repository.LibraryRepository
import org.ole.planet.myplanet.repository.RatingRepository
import org.ole.planet.myplanet.repository.SearchRepository
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.utilities.SharedPrefManager

@kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CoursesViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var courseRepository: CourseRepository
    private lateinit var ratingRepository: RatingRepository
    private lateinit var courseProgressRepository: CourseProgressRepository
    private lateinit var libraryRepository: LibraryRepository
    private lateinit var searchRepository: SearchRepository

    @MockK(relaxed = true)
    private lateinit var syncManager: SyncManager

    @MockK(relaxed = true)
    private lateinit var sharedPrefManager: SharedPrefManager

    @MockK(relaxed = true)
    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var viewModel: CoursesViewModel

    private val userId = "user123"

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        MockKAnnotations.init(this, relaxUnitFun = true)

        val courses = listOf(
            createCourse("course-1", "Zebra", emptyList()),
            createCourse("course-2", "Alpha", listOf(userId))
        )

        courseRepository = FakeCourseRepository(courses)
        ratingRepository = FakeRatingRepository(
            mapOf(
                "course-2" to JsonObject().apply {
                    addProperty("averageRating", 4.5f)
                    addProperty("total", 3)
                    addProperty("ratingByUser", 4)
                }
            )
        )
        courseProgressRepository = FakeCourseProgressRepository(
            mapOf(
                "course-2" to JsonObject().apply {
                    addProperty("max", 5)
                    addProperty("current", 2)
                }
            )
        )
        libraryRepository = FakeLibraryRepository()
        searchRepository = FakeSearchRepository()

        every { sharedPreferences.getBoolean("fastSync", false) } returns false
        every { sharedPreferences.getString("serverURL", any()) } returns "http://planet.test"
        every { sharedPreferences.edit() } returns mockk(relaxed = true)
        every { sharedPrefManager.isCoursesSynced() } returns true

        viewModel = CoursesViewModel(
            courseRepository,
            libraryRepository,
            ratingRepository,
            courseProgressRepository,
            searchRepository,
            syncManager,
            sharedPrefManager,
            sharedPreferences
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun loadCourses_emitsSuccessWithCourseDetails() = runTest {
        viewModel.loadCourses(userId, isMyCourseLib = false)

        val state = viewModel.coursesState.filterIsInstance<CoursesViewModel.CoursesUiState.Success>().first()

        assertEquals(2, state.courses.size)
        assertFalse(state.courses.first().isMyCourse)
        assertTrue(state.courses.last().isMyCourse)
        assertEquals("Alpha", state.courses.last().courseTitle)
        assertEquals(4.5f, state.ratings["course-2"]?.get("averageRating")?.asFloat)
        assertEquals(2, state.progress["course-2"]?.get("current")?.asInt)
    }

    @Test
    fun loadCourses_filtersToMyCoursesWhenRequested() = runTest {
        viewModel.loadCourses(userId, isMyCourseLib = true)

        val state = viewModel.coursesState.filterIsInstance<CoursesViewModel.CoursesUiState.Success>().first()

        assertEquals(1, state.courses.size)
        assertTrue(state.courses.first().isMyCourse)
        assertEquals("Alpha", state.courses.first().courseTitle)
    }

    @Test
    fun startCoursesSyncIfNeeded_emitsSyncStates() = runTest {
        every { sharedPreferences.getBoolean("fastSync", false) } returns true
        every { sharedPrefManager.isCoursesSynced() } returns false
        every { sharedPrefManager.setCoursesSynced(true) } just Runs

        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { sharedPreferences.edit() } returns editor

        mockkObject(MainApplication.Companion)
        coEvery { MainApplication.isServerReachable(any()) } returns true

        every { syncManager.start(any(), any(), any()) } answers {
            val listener = firstArg<SyncListener>()
            listener.onSyncStarted()
            listener.onSyncComplete()
        }

        viewModel.loadCourses(userId, isMyCourseLib = false)

        viewModel.startCoursesSyncIfNeeded()

        viewModel.syncState.filterIsInstance<CoursesViewModel.SyncState.Success>().first()

        assertTrue(viewModel.syncState.value is CoursesViewModel.SyncState.Idle)
        verify { sharedPrefManager.setCoursesSynced(true) }
    }

    private fun createCourse(id: String, title: String, owners: List<String>): RealmMyCourse {
        return RealmMyCourse().apply {
            this.id = id
            this.courseId = id
            this.courseTitle = title
            this.createdDate = title.hashCode().toLong()
            this.userId = RealmList<String>().apply { addAll(owners) }
        }
    }

    private class FakeCourseRepository(
        private val courses: List<RealmMyCourse>
    ) : CourseRepository {
        override suspend fun getAllCourses(): List<RealmMyCourse> = courses

        override suspend fun getEnrolledCourses(userId: String): List<RealmMyCourse> {
            return courses.filter { it.userId?.contains(userId) == true }
        }

        override suspend fun updateMyCourseFlag(courseId: String, isMyCourse: Boolean) = Unit
    }

    private class FakeRatingRepository(
        private val ratings: Map<String?, JsonObject>
    ) : RatingRepository {
        override suspend fun getRatings(type: String, userId: String?): Map<String?, JsonObject> = ratings
    }

    private class FakeCourseProgressRepository(
        private val progress: Map<String?, JsonObject>
    ) : CourseProgressRepository {
        override suspend fun getCourseProgress(userId: String?): Map<String?, JsonObject> = progress
    }

    private class FakeLibraryRepository : LibraryRepository {
        override suspend fun getAllLibraryItems(): List<RealmMyLibrary> = emptyList()

        override suspend fun getLibraryItemById(id: String): RealmMyLibrary? = null

        override suspend fun getLibraryItemByResourceId(resourceId: String): RealmMyLibrary? = null

        override suspend fun getLibraryByResourceId(resourceId: String): RealmMyLibrary? = null

        override suspend fun getLibraryItemsByLocalAddress(localAddress: String): List<RealmMyLibrary> = emptyList()

        override suspend fun getLibraryListForUser(userId: String?): List<RealmMyLibrary> = emptyList()

        override suspend fun getAllLibraryList(): List<RealmMyLibrary> = emptyList()

        override suspend fun getCourseLibraryItems(courseIds: List<String>): List<RealmMyLibrary> = emptyList()

        override suspend fun saveLibraryItem(item: RealmMyLibrary) = Unit

        override suspend fun markResourceAdded(userId: String?, resourceId: String) = Unit

        override suspend fun updateUserLibrary(
            resourceId: String,
            userId: String,
            isAdd: Boolean
        ): RealmMyLibrary? = null

        override suspend fun deleteLibraryItem(id: String) = Unit

        override suspend fun updateLibraryItem(id: String, updater: (RealmMyLibrary) -> Unit) = Unit
    }

    private class FakeSearchRepository : SearchRepository {
        override suspend fun saveSearchActivity(
            userId: String?,
            userPlanetCode: String?,
            userParentCode: String?,
            searchText: String,
            tags: List<org.ole.planet.myplanet.model.RealmTag>,
            gradeLevel: String,
            subjectLevel: String
        ) = Unit
    }
}
