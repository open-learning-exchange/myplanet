package org.ole.planet.myplanet.utils

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.Course
import org.ole.planet.myplanet.model.MyCourse
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class CoursesItemUtilsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testTimeProvider = TestTimeProvider(currentTime = 1000L)
    private lateinit var activity: AppCompatActivity

    @Before
    fun setUp() {
        CoursesItemUtils.resetForTesting()
        activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)
        CoursesItemUtils.timeProvider = testTimeProvider
        mockkObject(MyCourse)
    }

    @After
    fun tearDown() {
        unmockkObject(MyCourse)
        CoursesItemUtils.resetForTesting()
    }

    @Test
    fun testBindCover_cachesExistenceCheckWithinTtlAndRestatsAfterTtl() {
        val testFile = tempFolder.newFile("cover.jpg")
        val spyFile = spyk(testFile)

        val course = Course(
            courseId = "c1",
            courseTitle = "Test Course",
            description = "Description",
            gradeLevel = "1",
            subjectLevel = "Beginner",
            createdDate = 0L,
            coverFileName = "cover.jpg"
        )
        val coverContainer = View(activity)
        val ivCover = ImageView(activity)
        val ivSubjectIcon = ImageView(activity)

        every { MyCourse.getCoverImageFile(activity, "c1", "cover.jpg") } returns spyFile

        // First call at time 1000L -> checks existence on disk
        CoursesItemUtils.bindCover(
            context = activity,
            viewMode = ListViewMode.LIST,
            course = course,
            subject = CourseSubject.MATHEMATICS,
            coverContainer = coverContainer,
            ivCover = ivCover,
            ivSubjectIcon = ivSubjectIcon
        )

        verify(exactly = 1) { spyFile.exists() }

        // Second call within TTL (time = 2000L, delta = 1000ms < 5000ms TTL)
        testTimeProvider.currentTime = 2000L

        CoursesItemUtils.bindCover(
            context = activity,
            viewMode = ListViewMode.LIST,
            course = course,
            subject = CourseSubject.MATHEMATICS,
            coverContainer = coverContainer,
            ivCover = ivCover,
            ivSubjectIcon = ivSubjectIcon
        )

        // Count of exists() calls should still be 1 (cached result used)
        verify(exactly = 1) { spyFile.exists() }

        // Third call after TTL (time = 6001L, delta = 5001ms > 5000ms TTL)
        testTimeProvider.currentTime = 6001L

        CoursesItemUtils.bindCover(
            context = activity,
            viewMode = ListViewMode.LIST,
            course = course,
            subject = CourseSubject.MATHEMATICS,
            coverContainer = coverContainer,
            ivCover = ivCover,
            ivSubjectIcon = ivSubjectIcon
        )

        // Count of exists() calls should now be 2 (re-stated on disk)
        verify(exactly = 2) { spyFile.exists() }
    }

    class TestFragment : Fragment() {
        lateinit var ivCover: ImageView
        lateinit var coverContainer: View
        lateinit var ivSubjectIcon: ImageView

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val root = FrameLayout(requireContext())
            coverContainer = View(requireContext())
            ivCover = ImageView(requireContext())
            ivSubjectIcon = ImageView(requireContext())
            root.addView(coverContainer)
            root.addView(ivCover)
            root.addView(ivSubjectIcon)
            return root
        }
    }

    @Test
    fun testBindCover_withAttachedImageViewInFragment_succeedsAndIsVisible() {
        val testFile = tempFolder.newFile("cover.jpg")
        val course = Course(
            courseId = "c1",
            courseTitle = "Test Course",
            description = "Description",
            gradeLevel = "1",
            subjectLevel = "Beginner",
            createdDate = 0L,
            coverFileName = "cover.jpg"
        )
        every { MyCourse.getCoverImageFile(any(), "c1", "cover.jpg") } returns testFile

        val fragment = TestFragment()
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment)
            .commitNow()

        CoursesItemUtils.bindCover(
            context = activity,
            viewMode = ListViewMode.LIST,
            course = course,
            subject = CourseSubject.MATHEMATICS,
            coverContainer = fragment.coverContainer,
            ivCover = fragment.ivCover,
            ivSubjectIcon = fragment.ivSubjectIcon
        )

        assertEquals(View.VISIBLE, fragment.ivCover.visibility)

        val appContext = ApplicationProvider.getApplicationContext<Context>()
        Glide.with(appContext).clear(fragment.ivCover)
        val target = fragment.ivCover.getTag(com.bumptech.glide.R.id.glide_custom_view_target_tag) as? Target<*>
        val request = target?.request
        assertTrue(request == null || request.isCleared)
    }

    @Test
    fun testBindCover_withUnattachedImageViewAndAppContext_succeedsWithoutThrowing() {
        val testFile = tempFolder.newFile("cover.jpg")
        val course = Course(
            courseId = "c1",
            courseTitle = "Test Course",
            description = "Description",
            gradeLevel = "1",
            subjectLevel = "Beginner",
            createdDate = 0L,
            coverFileName = "cover.jpg"
        )
        every { MyCourse.getCoverImageFile(any(), "c1", "cover.jpg") } returns testFile

        val unattachedIvActivity = ImageView(activity)
        val coverContainerActivity = View(activity)
        val ivSubjectIconActivity = ImageView(activity)

        CoursesItemUtils.bindCover(
            context = activity,
            viewMode = ListViewMode.LIST,
            course = course,
            subject = CourseSubject.MATHEMATICS,
            coverContainer = coverContainerActivity,
            ivCover = unattachedIvActivity,
            ivSubjectIcon = ivSubjectIconActivity
        )
        assertEquals(View.VISIBLE, unattachedIvActivity.visibility)

        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val unattachedIvApp = ImageView(appContext)
        val coverContainerApp = View(appContext)
        val ivSubjectIconApp = ImageView(appContext)

        CoursesItemUtils.bindCover(
            context = appContext,
            viewMode = ListViewMode.LIST,
            course = course,
            subject = CourseSubject.MATHEMATICS,
            coverContainer = coverContainerApp,
            ivCover = unattachedIvApp,
            ivSubjectIcon = ivSubjectIconApp
        )
        assertEquals(View.VISIBLE, unattachedIvApp.visibility)
    }
}
