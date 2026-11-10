package org.ole.planet.myplanet.ui.resources

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.ui.courses.CoursesFilterFragment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "es")
class AddResourceEnglishValuesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun pairAt(arrayRes: Int, index: Int) =
        context.resources.getStringArray(arrayRes)[index] to CoursesFilterFragment.englishValues(context, arrayRes)[index]

    @Test
    fun `resource values saved are English while the form shows Spanish`() {
        assertEquals("Texto" to "Text", pairAt(R.array.media, 0))
        assertEquals("Libro de texto" to "Textbook", pairAt(R.array.resource_type, 0))
        assertEquals("Predeterminado" to "Default", pairAt(R.array.array_resource_for, 0))
        assertEquals(
            context.resources.getStringArray(R.array.array_subjects).size,
            CoursesFilterFragment.englishValues(context, R.array.array_subjects).size
        )
        assertEquals(
            context.resources.getStringArray(R.array.array_levels).size,
            CoursesFilterFragment.englishValues(context, R.array.array_levels).size
        )
    }
}
