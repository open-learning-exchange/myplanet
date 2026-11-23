package org.ole.planet.myplanet.ui.courses

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.utils.CoursesItemUtils
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "es")
class CoursesFilterEnglishValuesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `grade and subject filter values are stored values while the spinner shows Spanish`() {
        assertEquals("Abierto", context.resources.getStringArray(R.array.grade_level)[1])
        assertEquals("Pre-Kindergarten", CoursesFilterFragment.filterValues(context, R.array.grade_level)[1])

        assertEquals("Principiante", context.resources.getStringArray(R.array.subject_level)[1])
        assertEquals("Beginner", CoursesFilterFragment.filterValues(context, R.array.subject_level)[1])
    }

    @Test
    fun `grade labels and stored values stay aligned`() {
        assertEquals(
            context.resources.getStringArray(R.array.grade_level_values).size,
            context.resources.getStringArray(R.array.grade_level).size
        )
    }

    @Test
    fun `stored Pre-Kindergarten grade is labelled Open`() {
        assertEquals("Abierto", CoursesItemUtils.gradeLevelLabel(context, "Pre-Kindergarten"))
        assertEquals("Jardín de infantes", CoursesItemUtils.gradeLevelLabel(context, "Kindergarten"))
        assertEquals("Unknown", CoursesItemUtils.gradeLevelLabel(context, "Unknown"))
    }
}
