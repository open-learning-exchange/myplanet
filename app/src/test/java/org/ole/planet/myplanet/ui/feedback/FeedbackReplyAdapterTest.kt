package org.ole.planet.myplanet.ui.feedback

import android.app.Application
import android.content.Context
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.FeedbackReply
import org.ole.planet.myplanet.utils.TimeUtils
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FeedbackReplyAdapterTest {

    private lateinit var context: Context
    private lateinit var adapter: FeedbackReplyAdapter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)
        adapter = FeedbackReplyAdapter(context)
        mockkObject(TimeUtils)
    }

    @After
    fun tearDown() {
        unmockkObject(TimeUtils)
    }

    private fun submitListAndIdle(list: List<FeedbackReply>) {
        var committed = false
        adapter.submitList(list) {
            committed = true
        }
        while (!committed) {
            ShadowLooper.idleMainLooper()
        }
    }

    @Test
    fun `onBindViewHolder binds date user and message correctly`() {
        val rawDate = "1600000000000"
        val reply = FeedbackReply("Hello World", "John Doe", rawDate)
        submitListAndIdle(listOf(reply))

        val parent = FrameLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        verify(exactly = 1) { TimeUtils.getFormattedDateWithTime(rawDate.toLong()) }

        val expectedDate = TimeUtils.getFormattedDateWithTime(rawDate.toLong())
        assertEquals(expectedDate, holder.binding.tvDate.text.toString())
        assertEquals("John Doe", holder.binding.tvUser.text.toString())
        assertEquals("Hello World", holder.binding.tvMessage.text.toString())
    }
}
