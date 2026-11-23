package org.ole.planet.myplanet.ui.courses

import android.app.Application
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.StepItem
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CoursesStepsAdapterTest {

    private lateinit var context: Context
    private val onStepClicked: (String) -> Unit = mockk(relaxed = true)
    private lateinit var adapter: CoursesStepsAdapter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)
        adapter = CoursesStepsAdapter(context, onStepClicked)
    }

    @Test
    fun `test description equals getString for each row`() {
        val items = listOf(
            StepItem(id = "1", stepTitle = "Step 1", questionCount = 3, isDescriptionVisible = true),
            StepItem(id = "2", stepTitle = "Step 2", questionCount = 12, isDescriptionVisible = false)
        )
        adapter.submitList(items) {
            val parent = FrameLayout(context)
            val holder0 = adapter.onCreateViewHolder(parent, 0)
            adapter.onBindViewHolder(holder0, 0)
            val holder1 = adapter.onCreateViewHolder(parent, 0)
            adapter.onBindViewHolder(holder1, 1)

            val tvDescription0 = holder0.itemView.findViewById<android.widget.TextView>(R.id.tv_description)
            val tvDescription1 = holder1.itemView.findViewById<android.widget.TextView>(R.id.tv_description)

            assertEquals(context.getString(R.string.test_size, 3), tvDescription0.text.toString())
            assertEquals(context.getString(R.string.test_size, 12), tvDescription1.text.toString())
        }
        ShadowLooper.idleMainLooper()
    }

    @Test
    @Config(qualifiers = "ar")
    fun `test arabic locale formatting`() {
        val items = listOf(
            StepItem(id = "1", stepTitle = "Step 1", questionCount = 3, isDescriptionVisible = true),
            StepItem(id = "2", stepTitle = "Step 2", questionCount = 12, isDescriptionVisible = false)
        )
        adapter.submitList(items) {
            val parent = FrameLayout(context)
            val holder1 = adapter.onCreateViewHolder(parent, 0)
            adapter.onBindViewHolder(holder1, 1)

            val tvDescription1 = holder1.itemView.findViewById<android.widget.TextView>(R.id.tv_description)

            assertEquals(context.getString(R.string.test_size, 12), tvDescription1.text.toString())
        }
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun `test payload isDescriptionVisible toggles visibility`() {
        val items = listOf(
            StepItem(id = "1", stepTitle = "Step 1", questionCount = 3, isDescriptionVisible = true)
        )
        adapter.submitList(items) {
            val parent = FrameLayout(context)
            val holder = adapter.onCreateViewHolder(parent, 0)
            adapter.onBindViewHolder(holder, 0)

            val tvDescription = holder.itemView.findViewById<android.widget.TextView>(R.id.tv_description)
            assertEquals(View.VISIBLE, tvDescription.visibility)

            adapter.onBindViewHolder(holder, 0, mutableListOf(false))
            assertEquals(View.GONE, tvDescription.visibility)

            adapter.onBindViewHolder(holder, 0, mutableListOf(true))
            assertEquals(View.VISIBLE, tvDescription.visibility)
        }
        ShadowLooper.idleMainLooper()
    }
}
