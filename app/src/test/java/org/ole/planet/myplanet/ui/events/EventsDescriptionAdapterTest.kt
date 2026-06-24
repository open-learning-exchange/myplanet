package org.ole.planet.myplanet.ui.events

import android.content.Context
import android.os.Build
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P], application = android.app.Application::class)
class EventsDescriptionAdapterTest {

    private lateinit var context: Context
    private lateinit var adapter: EventsDescriptionAdapter

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        adapter = EventsDescriptionAdapter()
    }

    @Test
    fun `test adapter item binding and diff`() {
        val initialList = listOf(
            EventsDescriptionAdapter.DescriptionItem("Date", "2023-10-27"),
            EventsDescriptionAdapter.DescriptionItem("Time", "10:00 AM")
        )

        val commitCallback = Runnable {}
        adapter.submitList(initialList, commitCallback)
        // Ensure UI thread has processed the updates in Robolectric
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertEquals(2, adapter.itemCount)

        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)

        adapter.onBindViewHolder(holder, 0)
        assertEquals("Date : ", holder.title.text.toString())
        assertEquals("2023-10-27", holder.description.text.toString())

        adapter.onBindViewHolder(holder, 1)
        assertEquals("Time : ", holder.title.text.toString())
        assertEquals("10:00 AM", holder.description.text.toString())

        val updatedList = listOf(
            EventsDescriptionAdapter.DescriptionItem("Date", "2023-10-28"),
            EventsDescriptionAdapter.DescriptionItem("Location", "Online")
        )

        adapter.submitList(updatedList, commitCallback)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertEquals(2, adapter.itemCount)

        adapter.onBindViewHolder(holder, 0)
        assertEquals("Date : ", holder.title.text.toString())
        assertEquals("2023-10-28", holder.description.text.toString())

        adapter.onBindViewHolder(holder, 1)
        assertEquals("Location : ", holder.title.text.toString())
        assertEquals("Online", holder.description.text.toString())
    }
}
