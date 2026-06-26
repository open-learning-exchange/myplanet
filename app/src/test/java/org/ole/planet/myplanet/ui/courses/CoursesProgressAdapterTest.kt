package org.ole.planet.myplanet.ui.courses

import android.content.Context
import android.os.Build
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P], application = android.app.Application::class)
class CoursesProgressAdapterTest {

    private lateinit var context: Context
    private lateinit var adapter: CoursesProgressAdapter

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        adapter = CoursesProgressAdapter(context)
    }

    @Test
    fun `test holder recycling with different stepMistake counts`() {
        val manyStepsItem = JsonObject().apply {
            addProperty("courseId", "1")
            addProperty("courseName", "Course 1")
            add("stepMistake", JsonObject().apply {
                addProperty("0", 1)
                addProperty("1", 2)
                addProperty("2", 3)
            })
        }

        val fewerStepsItem = JsonObject().apply {
            addProperty("courseId", "2")
            addProperty("courseName", "Course 2")
            add("stepMistake", JsonObject().apply {
                addProperty("0", 5)
            })
        }

        val noStepsItem = JsonObject().apply {
            addProperty("courseId", "3")
            addProperty("courseName", "Course 3")
            // Intentionally omit stepMistake to test that missing data clears the UI
        }

        adapter.submitList(listOf(manyStepsItem, fewerStepsItem, noStepsItem))

        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)

        adapter.onBindViewHolder(holder, 0)
        assertEquals(3, holder.binding.llProgress.childCount)

        adapter.onBindViewHolder(holder, 1)
        assertEquals(1, holder.binding.llProgress.childCount)

        adapter.onBindViewHolder(holder, 2)
        assertEquals(0, holder.binding.llProgress.childCount)
    }
}
