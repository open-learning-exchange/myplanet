package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import androidx.viewpager2.widget.ViewPager2
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.data.room.dao.CourseStepDao
import org.ole.planet.myplanet.model.CourseStep
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.ui.dashboard.TestDashboardElementActivity
import org.ole.planet.myplanet.utils.UrlUtils
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class TakeCourseFragmentStepLockTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var sharedPrefManager: SharedPrefManager

    @Inject
    lateinit var courseStepDao: CourseStepDao

    private val viewModel: TakeCourseViewModel = mockk(relaxed = true)

    // Step 1 (index 0) requires a pass the learner hasn't earned, so steps 2 and 3 are locked
    private val steps = listOf(
        CourseStep(id = "step_1", courseId = COURSE_ID, stepTitle = "One", passingRequired = true),
        CourseStep(id = "step_2", courseId = COURSE_ID, stepTitle = "Two"),
        CourseStep(id = "step_3", courseId = COURSE_ID, stepTitle = "Three")
    )

    @Before
    fun setUp() {
        hiltRule.inject()
        UrlUtils.init(sharedPrefManager)
        // The step pages load their own step from the database
        runBlocking { courseStepDao.upsertAll(steps) }
    }

    @Test
    fun resume_stopsAtTheStepThatMustBePassed() {
        val fragment = launchFragment(blockingStepIndex = 0, savedProgress = 3)

        assertEquals(1, viewPager(fragment).currentItem)
    }

    @Test
    fun next_staysOnTheStepThatMustBePassed() {
        val fragment = launchFragment(blockingStepIndex = 0, savedProgress = 1)

        clickNext(fragment)

        assertEquals(1, viewPager(fragment).currentItem)
    }

    @Test
    fun next_movesOnOnceTheStepIsPassed() {
        val fragment = launchFragment(blockingStepIndex = -1, savedProgress = 1)

        clickNext(fragment)

        assertEquals(2, viewPager(fragment).currentItem)
    }

    @Test
    fun seekBar_cannotJumpPastTheStepThatMustBePassed() {
        val fragment = launchFragment(blockingStepIndex = 0, savedProgress = 1)

        dragSeekBarTo(fragment, 2)

        assertEquals(1, viewPager(fragment).currentItem)
    }

    @Test
    fun stepListTap_onALockedStepIsIgnored() {
        val fragment = launchFragment(blockingStepIndex = 0, savedProgress = 1)

        fragment.navigateToStep("step_3")
        idle()

        assertEquals(1, viewPager(fragment).currentItem)
    }

    private fun launchFragment(blockingStepIndex: Int, savedProgress: Int): TakeCourseFragment {
        val user = UserEntity().apply { id = USER_ID }
        val course = MyCourse(id = COURSE_ID, courseId = COURSE_ID, courseTitle = "Course", userId = listOf(USER_ID))
        every { viewModel.uiState } returns MutableStateFlow(
            TakeCourseUiState.Success(course, steps, user, savedProgress, blockingStepIndex)
        )
        // The fragment re-reads both on every page change
        coEvery { viewModel.getBlockingStepIndex(any(), any(), any()) } returns blockingStepIndex
        coEvery { viewModel.getCurrentProgress(any(), any(), any()) } returns savedProgress

        val activity = Robolectric.buildActivity(TestDashboardElementActivity::class.java).setup().get()
        val fragment = TakeCourseFragment()
        fragment.arguments = Bundle().apply { putString("id", COURSE_ID) }
        TakeCourseFragment::class.java.getDeclaredField("viewModel\$delegate").apply {
            isAccessible = true
            set(fragment, lazyOf(viewModel))
        }
        activity.openCallFragment(fragment, "takeCourse")
        activity.supportFragmentManager.executePendingTransactions()
        idle()
        return fragment
    }

    private fun viewPager(fragment: TakeCourseFragment): ViewPager2 =
        fragment.requireView().findViewById(R.id.viewPager2)

    private fun clickNext(fragment: TakeCourseFragment) {
        fragment.requireView().findViewById<View>(R.id.next_step).performClick()
        idle()
    }

    private fun dragSeekBarTo(fragment: TakeCourseFragment, page: Int) {
        val seekBar = fragment.requireView().findViewById<SeekBar>(R.id.course_progress)
        shadowOf(seekBar).onSeekBarChangeListener.onProgressChanged(seekBar, page, true)
        idle()
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    companion object {
        private const val COURSE_ID = "course_lock"
        private const val USER_ID = "user_1"
    }
}
