package org.ole.planet.myplanet.ui.feedback

import android.app.Application
import android.content.Context
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
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
    private lateinit var originalTimeZone: TimeZone
    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        originalLocale = Locale.getDefault()
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)
        adapter = FeedbackReplyAdapter(context)
        mockkObject(TimeUtils)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
        Locale.setDefault(originalLocale)
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
    fun `tvDate equals TimeUtils getFormattedDateWithTime for a reply`() {
        val rawDate = "1600000000000"
        val reply = FeedbackReply("Hello", "User1", rawDate)
        submitListAndIdle(listOf(reply))

        val parent = FrameLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        val expected = TimeUtils.getFormattedDateWithTime(rawDate.toLong())
        assertEquals(expected, holder.binding.tvDate.text.toString())
    }

    @Test
    fun `binding same reply twice calls getFormattedDateWithTime only once`() {
        val rawDate = "1600000000000"
        val reply = FeedbackReply("Hello", "User1", rawDate)
        submitListAndIdle(listOf(reply))

        val parent = FrameLayout(context)
        val holder1 = adapter.onCreateViewHolder(parent, 0)
        val holder2 = adapter.onCreateViewHolder(parent, 0)

        adapter.onBindViewHolder(holder1, 0)
        adapter.onBindViewHolder(holder2, 0)

        verify(exactly = 1) { TimeUtils.getFormattedDateWithTime(rawDate.toLong()) }
    }

    @Test
    fun `time zone change invalidates date cache and formats under new zone`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)

        val rawDate = "1600000000000"
        val reply = FeedbackReply("Hello", "User1", rawDate)
        submitListAndIdle(listOf(reply))

        val parent = FrameLayout(context)
        val holder1 = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder1, 0)
        val firstText = holder1.binding.tvDate.text.toString()

        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kathmandu"))

        val holder2 = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder2, 0)
        val secondText = holder2.binding.tvDate.text.toString()

        verify(exactly = 2) { TimeUtils.getFormattedDateWithTime(rawDate.toLong()) }

        val expectedFresh = TimeUtils.getFormattedDateWithTime(rawDate.toLong())
        assertEquals(expectedFresh, secondText)
        assertNotEquals(firstText, secondText)
    }

    @Test
    fun `locale change invalidates date cache and formats under new locale`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)

        val rawDate = "1600000000000"
        val reply = FeedbackReply("Hello", "User1", rawDate)
        submitListAndIdle(listOf(reply))

        val parent = FrameLayout(context)
        val holder1 = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder1, 0)
        val firstText = holder1.binding.tvDate.text.toString()

        Locale.setDefault(Locale.FRENCH)

        val holder2 = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder2, 0)
        val secondText = holder2.binding.tvDate.text.toString()

        verify(exactly = 2) { TimeUtils.getFormattedDateWithTime(rawDate.toLong()) }

        val expectedFresh = TimeUtils.getFormattedDateWithTime(rawDate.toLong())
        assertEquals(expectedFresh, secondText)
        assertNotEquals(firstText, secondText)
    }

    @Test
    fun `non numeric date string currently throws NumberFormatException on raw toLong`() {
        val reply = FeedbackReply("Hello", "User1", "abc")
        submitListAndIdle(listOf(reply))

        val parent = FrameLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)

        assertThrows(NumberFormatException::class.java) {
            adapter.onBindViewHolder(holder, 0)
        }
    }
}
