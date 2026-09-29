package org.ole.planet.myplanet.ui.feedback

import android.app.Application
import android.content.Context
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.Feedback
import org.ole.planet.myplanet.utils.TimeUtils
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FeedbackAdapterTest {

    private lateinit var context: Context
    private lateinit var adapter: FeedbackAdapter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)
        adapter = FeedbackAdapter()
        mockkObject(TimeUtils)
    }

    @After
    fun tearDown() {
        unmockkObject(TimeUtils)
    }

    @Test
    fun `onBindViewHolder formats openTime once per bind and sets tvOpenDate and contentDescription`() {
        every { TimeUtils.getFormattedDate(1000L) } returns "Formatted Date 1000"

        val feedback = Feedback().apply {
            id = "f1"
            title = "Test Title"
            type = "Bug"
            priority = "yes"
            status = "open"
            openTime = 1000L
        }

        var committed = false
        adapter.submitList(listOf(feedback)) {
            committed = true
        }

        while (!committed) {
            ShadowLooper.idleMainLooper()
        }

        val parent = FrameLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        verify(exactly = 1) { TimeUtils.getFormattedDate(1000L) }

        assertEquals("Formatted Date 1000", holder.rowFeedbackBinding.tvOpenDate.text.toString())
        assertTrue(
            holder.rowFeedbackBinding.feedbackCardView.contentDescription.toString()
                .contains("Formatted Date 1000")
        )
    }

    @Test
    fun `onBindViewHolder caches formatted date across multiple binds of the same openTime`() {
        every { TimeUtils.getFormattedDate(2000L) } returns "Formatted Date 2000"

        val feedback1 = Feedback().apply {
            id = "f1"
            title = "Feedback 1"
            type = "Bug"
            priority = "yes"
            status = "open"
            openTime = 2000L
        }

        val feedback2 = Feedback().apply {
            id = "f2"
            title = "Feedback 2"
            type = "Suggestion"
            priority = "no"
            status = "closed"
            openTime = 2000L
        }

        var committed = false
        adapter.submitList(listOf(feedback1, feedback2)) {
            committed = true
        }

        while (!committed) {
            ShadowLooper.idleMainLooper()
        }

        val parent = FrameLayout(context)
        val holder1 = adapter.onCreateViewHolder(parent, 0)
        val holder2 = adapter.onCreateViewHolder(parent, 0)

        adapter.onBindViewHolder(holder1, 0)
        adapter.onBindViewHolder(holder2, 1)

        verify(exactly = 1) { TimeUtils.getFormattedDate(2000L) }

        assertEquals("Formatted Date 2000", holder1.rowFeedbackBinding.tvOpenDate.text.toString())
        assertEquals("Formatted Date 2000", holder2.rowFeedbackBinding.tvOpenDate.text.toString())
    }
}
