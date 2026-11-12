package org.ole.planet.myplanet.ui.exam

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
class UserInformationLevelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `saved level is English while the spinner shows Spanish`() {
        assertEquals(
            listOf("Principiante", "Intermedio", "Avanzado", "Experto"),
            context.resources.getStringArray(R.array.level).toList()
        )
        assertEquals(
            listOf("Beginner", "Intermediate", "Advanced", "Expert"),
            CoursesFilterFragment.englishValues(context, R.array.level).toList()
        )
    }
}
