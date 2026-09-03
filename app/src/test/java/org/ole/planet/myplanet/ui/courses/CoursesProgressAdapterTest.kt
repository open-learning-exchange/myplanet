package org.ole.planet.myplanet.ui.courses

import android.app.Application
import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.CoursesProgressRow
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
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
        val manyStepsItem = CoursesProgressRow(
            courseId = "1",
            courseName = "Course 1",
            progressCurrent = null,
            progressMax = null,
            mistakes = null,
            stepMistake = mapOf("0" to 1, "1" to 2, "2" to 3)
        )

        val fewerStepsItem = CoursesProgressRow(
            courseId = "2",
            courseName = "Course 2",
            progressCurrent = null,
            progressMax = null,
            mistakes = null,
            stepMistake = mapOf("0" to 5)
        )

        val noStepsItem = CoursesProgressRow(
            courseId = "3",
            courseName = "Course 3",
            progressCurrent = null,
            progressMax = null,
            mistakes = null,
            stepMistake = null
        )

        adapter.submitList(listOf(manyStepsItem, fewerStepsItem, noStepsItem))

        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)

        adapter.onBindViewHolder(holder, 0)
        assertEquals(3, holder.binding.llProgress.childCount)
        var row = holder.binding.llProgress.getChildAt(0) as LinearLayout
        assertEquals("1", (row.getChildAt(0) as TextView).text)
        assertEquals("1", (row.getChildAt(1) as TextView).text)
        row = holder.binding.llProgress.getChildAt(1) as LinearLayout
        assertEquals("2", (row.getChildAt(0) as TextView).text)
        assertEquals("2", (row.getChildAt(1) as TextView).text)
        row = holder.binding.llProgress.getChildAt(2) as LinearLayout
        assertEquals("3", (row.getChildAt(0) as TextView).text)
        assertEquals("3", (row.getChildAt(1) as TextView).text)

        adapter.onBindViewHolder(holder, 1)
        assertEquals(1, holder.binding.llProgress.childCount)
        row = holder.binding.llProgress.getChildAt(0) as LinearLayout
        assertEquals("1", (row.getChildAt(0) as TextView).text)
        assertEquals("5", (row.getChildAt(1) as TextView).text)

        adapter.onBindViewHolder(holder, 2)
        assertEquals(0, holder.binding.llProgress.childCount)
    }

    @Test
    fun `step views use the cached daynight text color`() {
        val item = CoursesProgressRow(
            courseId = "1",
            courseName = "Course 1",
            progressCurrent = null,
            progressMax = null,
            mistakes = null,
            stepMistake = mapOf("0" to 1)
        )

        adapter.submitList(listOf(item))

        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        val row = holder.binding.llProgress.getChildAt(0) as LinearLayout
        val expectedColor = ContextCompat.getColor(context, R.color.daynight_textColor)
        assertNotEquals(0, expectedColor)
        assertEquals(expectedColor, (row.getChildAt(0) as TextView).currentTextColor)
        assertEquals(expectedColor, (row.getChildAt(1) as TextView).currentTextColor)
    }
}
