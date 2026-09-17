package org.ole.planet.myplanet.utils

import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.Course
import org.ole.planet.myplanet.model.MyCourse
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class CoursesItemUtilsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testTimeProvider = TestTimeProvider(currentTime = 1000L)
    private lateinit var activity: AppCompatActivity

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)
        CoursesItemUtils.timeProvider = testTimeProvider
        mockkObject(MyCourse)
    }

    @After
    fun tearDown() {
        unmockkObject(MyCourse)
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
}
