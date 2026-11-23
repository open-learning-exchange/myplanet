package org.ole.planet.myplanet.ui.surveys

import android.app.Application
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.SurveyRow
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SurveysAdapterTest {

    private lateinit var context: Context
    private lateinit var adapter: SurveysAdapter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)
        adapter = SurveysAdapter(context, "user1", isTeam = false, teamId = null, onAdoptSurveyListener = mockk(relaxed = true))
    }

    private fun row(id: String, description: String?) =
        SurveyRow(StepExam(id = id, name = "Survey $id").apply { this.description = description }, null, null)

    @Test
    fun `recycled row without a description hides the previous survey's description`() {
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        val tvDescription = holder.itemView.findViewById<TextView>(R.id.tv_description)

        holder.bind(row("s1", "First survey description"))
        assertEquals(View.VISIBLE, tvDescription.visibility)
        assertEquals("First survey description", tvDescription.text.toString())

        holder.bind(row("s2", null))
        assertEquals(View.GONE, tvDescription.visibility)
        assertEquals("", tvDescription.text.toString())
    }
}
