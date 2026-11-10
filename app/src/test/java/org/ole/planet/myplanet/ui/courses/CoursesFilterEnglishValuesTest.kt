package org.ole.planet.myplanet.ui.courses

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "es")
class CoursesFilterEnglishValuesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `grade and subject filter values are English while the spinner shows Spanish`() {
        assertEquals("Pre-Jardín de infancia", context.resources.getStringArray(R.array.grade_level)[1])
        assertEquals("Pre-Kindergarten", CoursesFilterFragment.englishValues(context, R.array.grade_level)[1])

        assertEquals("Principiante", context.resources.getStringArray(R.array.subject_level)[1])
        assertEquals("Beginner", CoursesFilterFragment.englishValues(context, R.array.subject_level)[1])
    }
}
