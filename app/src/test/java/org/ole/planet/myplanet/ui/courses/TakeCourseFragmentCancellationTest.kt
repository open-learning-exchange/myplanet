package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import android.os.Looper
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.ui.dashboard.TestDashboardElementActivity
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.Utilities
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class TakeCourseFragmentCancellationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var sharedPrefManager: SharedPrefManager

    private val viewModel: TakeCourseViewModel = mockk(relaxed = true)

    @Before
    fun setUp() {
        hiltRule.inject()
        UrlUtils.init(sharedPrefManager)
        mockkObject(Utilities)
        every { Utilities.toast(any(), any(), any()) } just Runs
        every { viewModel.uiState } returns MutableStateFlow(TakeCourseUiState.Loading)
        coEvery { viewModel.getCourseById(COURSE_ID) } returns null
    }

    @After
    fun tearDown() {
        unmockkObject(Utilities)
    }

    @Test
    fun addRemoveCourse_whenEnrolmentIsCancelled_doesNotReportFailure() {
        coEvery { viewModel.joinCourse(COURSE_ID, USER_ID) } returns
            Result.failure(CancellationException("view destroyed mid-enrolment"))

        addRemoveCourse(launchFragment())

        verify(exactly = 0) { Utilities.toast(any(), any(), any()) }
    }

    @Test
    fun addRemoveCourse_whenEnrolmentFails_reportsFailure() {
        coEvery { viewModel.joinCourse(COURSE_ID, USER_ID) } returns
            Result.failure(IllegalStateException("boom"))

        addRemoveCourse(launchFragment())

        verify(exactly = 1) {
            Utilities.toast(any(), match { it.toString().contains("Failed to update course") }, any())
        }
    }

    private fun launchFragment(): TakeCourseFragment {
        val activity = Robolectric.buildActivity(TestDashboardElementActivity::class.java).setup().get()
        val fragment = TakeCourseFragment()
        fragment.arguments = Bundle().apply { putString("id", COURSE_ID) }

        TakeCourseFragment::class.java.getDeclaredField("viewModel\$delegate").apply {
            isAccessible = true
            set(fragment, lazyOf(viewModel))
        }
        activity.openCallFragment(fragment, "takeCourse")
        activity.supportFragmentManager.executePendingTransactions()
        // The stubbed ui state stays Loading, so bindCourse never supplies a user.
        TakeCourseFragment::class.java.getDeclaredField("userModel").apply {
            isAccessible = true
            set(fragment, UserEntity().apply { id = USER_ID })
        }
        return fragment
    }

    private fun addRemoveCourse(fragment: TakeCourseFragment) {
        TakeCourseFragment::class.java.getDeclaredMethod("addRemoveCourse").apply {
            isAccessible = true
            invoke(fragment)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    companion object {
        private const val COURSE_ID = "course_1"
        private const val USER_ID = "user_1"
    }
}
