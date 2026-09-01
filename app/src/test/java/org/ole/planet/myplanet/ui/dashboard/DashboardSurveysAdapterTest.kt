package org.ole.planet.myplanet.ui.dashboard

import android.app.Application
import android.content.Context
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.ole.planet.myplanet.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DashboardSurveysAdapterTest {

    private lateinit var context: Context
    private lateinit var mockDialog: AlertDialog
    private var clickedPosition: Int? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(R.style.AppTheme)
        mockDialog = mock(AlertDialog::class.java)
        clickedPosition = null
    }

    @Test
    fun `test onCreateViewHolder and onBindViewHolder sets text and hoisted text color`() {
        val adapter = DashboardSurveysAdapter(
            onItemClick = { position -> clickedPosition = position },
            dialog = mockDialog
        )
        adapter.submitList(listOf("Survey 1", "Survey 2"))

        val parent = FrameLayout(context)
        val viewHolder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(viewHolder, 0)

        val expectedColor = ContextCompat.getColor(context, R.color.daynight_textColor)
        assertEquals("Survey 1", viewHolder.textView.text.toString())
        assertEquals(expectedColor, viewHolder.textColor)
        assertEquals(expectedColor, viewHolder.textView.currentTextColor)
    }

    @Test
    fun `test item click invokes callback and dismisses dialog`() {
        val adapter = DashboardSurveysAdapter(
            onItemClick = { position -> clickedPosition = position },
            dialog = mockDialog
        )
        val recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        adapter.submitList(listOf("Survey 1"))
        recyclerView.measure(0, 0)
        recyclerView.layout(0, 0, 100, 100)

        val viewHolder = recyclerView.findViewHolderForAdapterPosition(0) as DashboardSurveysAdapter.SurveyViewHolder
        viewHolder.itemView.performClick()

        assertEquals(0, clickedPosition)
        verify(mockDialog).dismiss()
    }
}
